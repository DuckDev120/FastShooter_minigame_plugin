package com.parlagames.shooter;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

public class TournamentManager {

    private final FastShooterPlugin plugin;
    private final DuelManager duelManager;

    public enum State {
        IDLE, OPEN, CLOSED, RUNNING
    }

    private State state = State.IDLE;

    private final Set<UUID> queue = new HashSet<>();
    private final Map<UUID, ItemStack[]> savedContents = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedArmor = new HashMap<>();
    private final Map<UUID, Location> savedLocations = new HashMap<>();

    // Tournament progression
    private int currentRound = 0;
    private List<UUID> activePlayersInRound = new ArrayList<>();
    private List<PlayerPair> roundMatches = new ArrayList<>();
    private int currentMatchIndex = 0;

    private final List<UUID> advancedList = new ArrayList<>();
    private final List<UUID> eliminatedList = new ArrayList<>();

    private final NamespacedKey statusKey;
    private final NamespacedKey locationKey;
    private final NamespacedKey invKey;
    private final NamespacedKey armorKey;

    public TournamentManager(FastShooterPlugin plugin) {
        this.plugin = plugin;
        this.duelManager = new DuelManager(plugin, this);

        this.statusKey = new NamespacedKey(plugin, "fastshooter_status");
        this.locationKey = new NamespacedKey(plugin, "fastshooter_loc");
        this.invKey = new NamespacedKey(plugin, "fastshooter_inv");
        this.armorKey = new NamespacedKey(plugin, "fastshooter_armor");
    }

    // No more YAML files needed.

    public void setTournamentState(State newState) {
        this.state = newState;
    }

    public State getState() {
        return state;
    }

    public boolean joinQueue(Player player) {
        if (state != State.OPEN) {
            player.sendMessage(plugin.getConfigManager().getMessage("tournament_closed"));
            return false;
        }

        if (queue.contains(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("already_in_queue"));
            return false;
        }

        saveAndPreparePlayer(player);
        queue.add(player.getUniqueId());
        teleportToCrowd(player);
        player.sendMessage(plugin.getConfigManager().getMessage("msg_queue_join"));
        return true;
    }

    public boolean leaveQueue(Player player) {
        if (!queue.contains(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("not_in_queue"));
            return false;
        }

        if (state == State.RUNNING) {
            // Handle mid-game quit
            eliminatedList.add(player.getUniqueId());
            activePlayersInRound.remove(player.getUniqueId());
            if (duelManager.isPlaying(player)) {
                duelManager.cancelDuelDueToLeave(player);
            }
        }

        queue.remove(player.getUniqueId());
        restorePlayer(player);
        player.sendMessage(plugin.getConfigManager().getMessage("msg_queue_leave"));
        return true;
    }

    public void startTournament() {
        if (state == State.RUNNING || queue.size() < 2) {
            return;
        }

        state = State.RUNNING;
        currentRound = 0;
        eliminatedList.clear();
        advancedList.clear();

        activePlayersInRound = new ArrayList<>(queue);

        Bukkit.broadcast(plugin.getConfigManager().getMessage("tournament_started"));

        beginNextRound();
    }

    public void forceStopTournament() {
        if (state == State.IDLE)
            return;
        state = State.IDLE;

        if (duelManager.isDuelActive()) {
            duelManager.cancelDuelDueToLeave(null); // Cancel generically
        }

        // Restore everyone in memory
        for (UUID uuid : new ArrayList<>(savedContents.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null)
                restorePlayer(p);
        }

        queue.clear();
        activePlayersInRound.clear();
        roundMatches.clear();
        advancedList.clear();
        eliminatedList.clear();
    }

    private void beginNextRound() {
        currentRound++;
        advancedList.clear(); // Reset for this round

        if (activePlayersInRound.size() == 1) {
            // We have a winner!
            Player champion = Bukkit.getPlayer(activePlayersInRound.get(0));
            if (champion != null) {
                Bukkit.broadcast(
                        plugin.getConfigManager().getMessageWithPlaceholders("msg_final_winner_title", "winner",
                                champion.getName()));
            }
            forceStopTournament();
            return;
        }

        if (activePlayersInRound.size() == 0) {
            // Draw? Everyone disconnected?
            forceStopTournament();
            return;
        }

        // Group into pairs
        Collections.shuffle(activePlayersInRound);
        roundMatches.clear();

        String roundNameStr = currentRound == 1 ? "Round 1" : "Round " + currentRound;
        if (activePlayersInRound.size() == 2)
            roundNameStr = "Finals";
        else if (activePlayersInRound.size() <= 4)
            roundNameStr = "Semi Finals";
        else if (activePlayersInRound.size() <= 8)
            roundNameStr = "Quarter Finals";

        Bukkit.broadcast(
                plugin.getConfigManager().getMessageWithPlaceholders("round_started", "round_name", roundNameStr));

        for (int i = 0; i < activePlayersInRound.size(); i += 2) {
            if (i + 1 < activePlayersInRound.size()) {
                roundMatches.add(new PlayerPair(activePlayersInRound.get(i), activePlayersInRound.get(i + 1)));
            } else {
                // Bye
                UUID byePlayer = activePlayersInRound.get(i);
                advancedList.add(byePlayer);
                Player p = Bukkit.getPlayer(byePlayer);
                if (p != null) {
                    Bukkit.broadcast(plugin.getConfigManager().getMessageWithPlaceholders("bye_announcement", "player",
                            p.getName()));
                }
            }
        }

        currentMatchIndex = 0;
        startNextMatch();
    }

    public void startNextMatch() {
        if (currentMatchIndex >= roundMatches.size()) {
            // End of round
            activePlayersInRound.clear();
            activePlayersInRound.addAll(advancedList);
            Bukkit.getScheduler().runTaskLater(plugin, this::beginNextRound, 60L);
            return;
        }

        PlayerPair match = roundMatches.get(currentMatchIndex);
        currentMatchIndex++;

        Player p1 = Bukkit.getPlayer(match.p1);
        Player p2 = Bukkit.getPlayer(match.p2);

        boolean p1Valid = p1 != null && p1.isOnline() && !p1.isDead();
        boolean p2Valid = p2 != null && p2.isOnline() && !p2.isDead();

        if (!p1Valid || !p2Valid) {
            // Someone disconnected or is dead. Automatically advance the online one if
            // applicable.
            if (p1Valid) {
                advancedList.add(p1.getUniqueId());
                eliminatedList.add(match.p2);
            } else if (p2Valid) {
                advancedList.add(p2.getUniqueId());
                eliminatedList.add(match.p1);
            } else {
                eliminatedList.add(match.p1);
                eliminatedList.add(match.p2);
            }
            startNextMatch(); // Instantly go next
            return;
        }

        // Broadcast duel
        Bukkit.broadcast(plugin.getConfigManager().getMessageWithPlaceholders("duel_announcement", "player1",
                p1.getName(), "player2", p2.getName()));

        boolean isFinal = (roundMatches.size() == 1 && advancedList.isEmpty());
        duelManager.startDuel(p1, p2, isFinal);
    }

    public void handleDuelEnd(Player winner, Player loser) {
        if (winner != null) {
            advancedList.add(winner.getUniqueId());
            teleportToCrowd(winner);
        }
        if (loser != null) {
            eliminatedList.add(loser.getUniqueId());
            teleportToCrowd(loser);
        }

        // Wait 3 seconds, start next match
        Bukkit.getScheduler().runTaskLater(plugin, this::startNextMatch, 60L);
    }

    private void saveAndPreparePlayer(Player player) {
        PlayerInventory inv = player.getInventory();
        savedContents.put(player.getUniqueId(), inv.getContents().clone());
        savedArmor.put(player.getUniqueId(), inv.getArmorContents().clone());
        savedLocations.put(player.getUniqueId(), player.getLocation().clone());

        inv.clear();
        inv.setArmorContents(null);
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(20.0);
        player.setFoodLevel(20);

        saveToPDC(player);
    }

    public void restorePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (!savedContents.containsKey(uuid))
            return;

        player.getInventory().setContents(savedContents.remove(uuid));
        player.getInventory().setArmorContents(savedArmor.remove(uuid));

        Location orig = savedLocations.remove(uuid);
        if (orig != null && player.isOnline()) {
            player.teleport(orig);
        }
        if (duelManager.isPlaying(player)) {
            player.hideBossBar(duelManager.getBossBar(player));
        }
    }

    public void teleportToCrowd(Player player) {
        Location pos1 = plugin.getConfigManager().getLocation("pos1");
        Location pos2 = plugin.getConfigManager().getLocation("pos2");
        if (pos1 == null || pos2 == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("setup_incomplete"));
            return;
        }
        // Randomly split them
        if (Math.random() > 0.5) {
            player.teleport(pos1);
        } else {
            player.teleport(pos2);
        }
    }

    public DuelManager getDuelManager() {
        return duelManager;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public int getCurrentMatchIndex() {
        return currentMatchIndex;
    }

    public int getTotalMatchesInRound() {
        return roundMatches.size();
    }

    public List<UUID> getAdvancedList() {
        return advancedList;
    }

    public List<UUID> getEliminatedList() {
        return eliminatedList;
    }

    public boolean hasMemory(Player player) {
        return savedContents.containsKey(player.getUniqueId());
    }

    private static class PlayerPair {
        UUID p1, p2;

        PlayerPair(UUID p1, UUID p2) {
            this.p1 = p1;
            this.p2 = p2;
        }
    }

    private String serializeItems(ItemStack[] items) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    private ItemStack[] deserializeItems(String data) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
                BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            ItemStack[] items = new ItemStack[dataInput.readInt()];
            for (int i = 0; i < items.length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            return items;
        } catch (Exception e) {
            return new ItemStack[0];
        }
    }

    public void saveToPDC(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(statusKey, PersistentDataType.STRING, "IN_TOURNAMENT");

        Location loc = savedLocations.get(player.getUniqueId());
        if (loc != null) {
            String locStr = loc.getWorld().getName() + ":" + loc.getX() + ":" + loc.getY() + ":" + loc.getZ() + ":"
                    + loc.getYaw() + ":" + loc.getPitch();
            pdc.set(locationKey, PersistentDataType.STRING, locStr);
        }

        ItemStack[] inv = savedContents.get(player.getUniqueId());
        if (inv != null)
            pdc.set(invKey, PersistentDataType.STRING, serializeItems(inv));

        ItemStack[] armor = savedArmor.get(player.getUniqueId());
        if (armor != null)
            pdc.set(armorKey, PersistentDataType.STRING, serializeItems(armor));
    }

    public void checkAndRestoreFromPDC(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (!pdc.has(statusKey, PersistentDataType.STRING))
            return;

        // RAM check
        if (savedContents.containsKey(player.getUniqueId())) {
            restorePlayer(player);
            return;
        }

        // Emergency Restore (Crash)
        String invStr = pdc.get(invKey, PersistentDataType.STRING);
        String armorStr = pdc.get(armorKey, PersistentDataType.STRING);
        String locStr = pdc.get(locationKey, PersistentDataType.STRING);

        if (invStr != null)
            player.getInventory().setContents(deserializeItems(invStr));
        if (armorStr != null)
            player.getInventory().setArmorContents(deserializeItems(armorStr));
        if (locStr != null) {
            String[] parts = locStr.split(":");
            org.bukkit.World world = Bukkit.getWorld(parts[0]);
            if (world != null) {
                Location loc = new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
                player.teleport(loc);
            }
        }

        // Cleanup PDC
        pdc.remove(statusKey);
        pdc.remove(locationKey);
        pdc.remove(invKey);
        pdc.remove(armorKey);
    }
}
