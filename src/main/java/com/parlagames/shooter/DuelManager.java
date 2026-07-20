package com.parlagames.shooter;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;

public class DuelManager {

    private final FastShooterPlugin plugin;
    private final TournamentManager tournamentManager;

    private boolean duelActive = false;
    private boolean awaitingJump = false;
    private Player player1;
    private Player player2;
    private int countdownTask = -1;
    private long shootStartTime = 0;
    private Player winnerShot = null;
    private long winnerShotTime = 0;

    // False Start BossBars
    private BossBar p1Bar;
    private BossBar p2Bar;
    private int p1Ammo = 3;
    private int p2Ammo = 3;

    public DuelManager(FastShooterPlugin plugin, TournamentManager tournamentManager) {
        this.plugin = plugin;
        this.tournamentManager = tournamentManager;
    }

    public void startDuel(Player p1, Player p2, boolean isFinal) {
        this.duelActive = true;
        this.awaitingJump = false;
        this.player1 = p1;
        this.player2 = p2;
        this.winnerShot = null;

        Location shoot1 = plugin.getConfigManager().getLocation("shoot1");
        Location shoot2 = plugin.getConfigManager().getLocation("shoot2");

        if (shoot1 == null || shoot2 == null) {
            p1.sendMessage(plugin.getConfigManager().getMessage("setup_incomplete"));
            p2.sendMessage(plugin.getConfigManager().getMessage("setup_incomplete"));
            tournamentManager.handleDuelEnd(null, null); // Skip
            return;
        }

        // Pitch exactly 0 for both spots
        shoot1.setPitch(0.0F);
        shoot2.setPitch(0.0F);

        // Teleport them facing away from each other
        shoot1.setDirection(shoot1.toVector().subtract(shoot2.toVector()));
        shoot2.setDirection(shoot2.toVector().subtract(shoot1.toVector()));

        p1.teleport(shoot1);
        p2.teleport(shoot2);

        // Setup weapon and HUD
        setupPlayerForDuel(p1);
        setupPlayerForDuel(p2);

        p1Ammo = 3;
        p2Ammo = 3;
        p1Bar = createBossBar(p1Ammo);
        p2Bar = createBossBar(p2Ammo);

        p1.showBossBar(p1Bar);
        p2.showBossBar(p2Bar);

        // Sync check: wait 10 ticks to ensure teleportation finished before starting
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p1.isOnline() && !p1.isDead() && p2.isOnline() && !p2.isDead()) {
                startCountdown(isFinal ? 5 : 3);
            } else {
                tournamentManager.handleDuelEnd(p1.isOnline() && !p1.isDead() ? p1 : p2,
                        p1.isOnline() && !p1.isDead() ? p2 : p1);
            }
        }, 10L);
    }

    private void setupPlayerForDuel(Player p) {

        // Give weapon
        // Give weapon and Auto-Equip
        ItemStack weapon = new ItemStack(plugin.getConfigManager().getWeaponMaterial());
        ItemMeta meta = weapon.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(plugin.getConfigManager().getWeaponData());
            meta.displayName(plugin.getConfigManager().getWeaponDisplayName());
            meta.lore(plugin.getConfigManager().getWeaponLore());
            weapon.setItemMeta(meta);
        }
        p.getInventory().setItem(0, weapon.clone());
        p.getInventory().setHeldItemSlot(0); // Force selection
        p.setCollidable(false); // Anti-push
    }

    private BossBar createBossBar(int ammo) {
        String symbol = plugin.getConfigManager().getRawMessageString("ammo_symbol");
        String ammoStr = symbol.repeat(Math.max(0, ammo));
        Component name = plugin.getConfigManager().getRawMessageWithPlaceholders("bossbar_title", "ammo", ammoStr);
        return BossBar.bossBar(name, (float) ammo / 3f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
    }

    private void updateBossBar(Player p, int ammo) {
        BossBar bar = (p.equals(player1)) ? p1Bar : p2Bar;
        String symbol = plugin.getConfigManager().getRawMessageString("ammo_symbol");
        String ammoStr = symbol.repeat(Math.max(0, ammo));
        Component name = plugin.getConfigManager().getRawMessageWithPlaceholders("bossbar_title", "ammo", ammoStr);
        bar.name(name);
        bar.progress((float) ammo / 3f);
    }

    private void startCountdown(int startingTime) {
        countdownTask = new BukkitRunnable() {
            int time = startingTime;

            @Override
            public void run() {
                if (!duelActive || player1 == null || player2 == null || !player1.isOnline() || !player2.isOnline()) {
                    cancel();
                    return;
                }

                Component subtitle = plugin.getConfigManager().getRawMessage("countdown_subtitle");
                Title.Times times = Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(800),
                        Duration.ofMillis(100));

                if (time > 0) {
                    Component mainTitle = plugin.getConfigManager().getRawMessageWithPlaceholders("countdown_title",
                            "time", String.valueOf(time));

                    Title titleObj = Title.title(mainTitle, subtitle, times);
                    player1.showTitle(titleObj);
                    player2.showTitle(titleObj);

                    player1.playSound(player1.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                    player2.playSound(player2.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);

                    time--;
                } else if (time == 0) {
                    Component mainTitle = plugin.getConfigManager().getRawMessage("go_title");
                    Title titleObj = Title.title(mainTitle, subtitle, times);
                    player1.showTitle(titleObj);
                    player2.showTitle(titleObj);

                    player1.playSound(player1.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                    player2.playSound(player2.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);

                    // Face each other exactly
                    faceEachOther(player1, player2);

                    // Allow shooting
                    shootStartTime = System.currentTimeMillis();
                    awaitingJump = true;

                    cancel();

                    scheduleTimeout();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L).getTaskId();
    }

    private void faceEachOther(Player p1, Player p2) {
        Location l1 = p1.getLocation();
        Location l2 = p2.getLocation();

        l1.setDirection(l2.toVector().subtract(l1.toVector()));
        l2.setDirection(l1.toVector().subtract(l2.toVector()));

        p1.teleport(l1);
        p2.teleport(l2);
    }

    private void scheduleTimeout() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (duelActive && awaitingJump && winnerShot == null) {
                // If draw, just pass both as null losers to handleDuelEnd
                endDuel(null);
            }
        }, 200L); // 10 seconds max duel
    }

    public void handleShoot(Player player, long shootTime) {
        if (!duelActive)
            return;
        if (!player.equals(player1) && !player.equals(player2))
            return;

        // FALSE START MECHANIC
        if (!awaitingJump) {
            handleFalseStart(player);
            return;
        }

        if (winnerShot != null) {
            if (winnerShot.equals(player))
                return;
            if (shootTime < winnerShotTime) {
                winnerShot = player;
                winnerShotTime = shootTime;
            }
            return;
        }

        winnerShot = player;
        winnerShotTime = shootTime;

        // Play custom shot sound globally at player location
        player.getWorld().playSound(player.getLocation(), "littleroom_kindletron3:littleroom.kindletron3.sword_swing04",
                1.5f, 1f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (duelActive && awaitingJump && winnerShot != null) {
                endDuel(winnerShot);
            }
        }, 1L);
    }

    private void handleFalseStart(Player player) {
        if (player.equals(player1)) {
            p1Ammo--;
            updateBossBar(player1, p1Ammo);
            if (p1Ammo <= 0) {
                // False start loss
                showFalseStartLoss(player1);
                endDuel(player2); // p2 wins by disqualification
            }
        } else {
            p2Ammo--;
            updateBossBar(player2, p2Ammo);
            if (p2Ammo <= 0) {
                showFalseStartLoss(player2);
                endDuel(player1);
            }
        }
    }

    private void showFalseStartLoss(Player player) {
        Component t = plugin.getConfigManager().getRawMessage("false_start_lose_title");
        Component sub = plugin.getConfigManager().getRawMessage("false_start_lose_subtitle");
        Title.Times times = Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(1000));
        player.showTitle(Title.title(t, sub, times));
    }

    private void endDuel(Player winner) {
        awaitingJump = false;
        duelActive = false;

        Player loser = null;
        if (winner != null) {
            loser = (winner.equals(player1)) ? player2 : player1;

            long reactionTime = winnerShotTime - shootStartTime;
            if (reactionTime < 0)
                reactionTime = 0; // Won by false start DQ

            Component winTitle = plugin.getConfigManager().getRawMessage("win_title");
            Component winSub = plugin.getConfigManager().getMessageWithPlaceholders("win_subtitle", "winner",
                    winner.getName(), "time", String.valueOf(reactionTime));
            Title.Times times = Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000),
                    Duration.ofMillis(1000));
            winner.showTitle(Title.title(winTitle, winSub, times));

            Component loseTitle = plugin.getConfigManager().getRawMessage("lose_title");
            Component loseSub = plugin.getConfigManager().getRawMessage("lose_subtitle");
            loser.showTitle(Title.title(loseTitle, loseSub, times));

            Component bcMsg = plugin.getConfigManager().getMessageWithPlaceholders("msg_winner_broadcast", "winner",
                    winner.getName(), "loser", loser.getName());
            Bukkit.getServer().sendMessage(bcMsg);

            // Spawn bullet trail
            spawnBulletTrail(winner.getEyeLocation(), loser.getEyeLocation());
        } else {
            // Draw
            Component drawMsg = plugin.getConfigManager().getMessage("msg_draw");
            player1.sendMessage(drawMsg);
            player2.sendMessage(drawMsg);

            player1.playSound(player1.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
            player2.playSound(player2.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);

            loser = player1;
            Player loser2 = player2; // We can only return 1 loser to tournament easily, so both lose?
            tournamentManager.handleDuelEnd(null, loser2); // Mark loser2 eliminated here manually
        }

        cleanupDuel(player1);
        cleanupDuel(player2);

        Player w = winner;
        Player l = loser;

        player1 = null;
        player2 = null;
        winnerShot = null;

        tournamentManager.handleDuelEnd(w, l);
    }

    private void cleanupDuel(Player player) {
        if (player == null)
            return;
        player.hideBossBar(player.equals(player1) ? p1Bar : p2Bar);
        player.getInventory().clear();
        player.setCollidable(true); // Restore collision
    }

    public void cancelDuelDueToLeave(Player leaver) {
        if (!duelActive)
            return;

        duelActive = false;
        awaitingJump = false;

        if (countdownTask != -1)
            Bukkit.getScheduler().cancelTask(countdownTask);

        if (player1 != null && player2 != null) {
            Player other = (leaver == null) ? null : (leaver.equals(player1) ? player2 : player1);

            if (other != null && other.isOnline()) {
                other.sendMessage(plugin.getConfigManager().getMessage("duel_cancelled"));
                cleanupDuel(other);
                // Other player advances by default if someone left mid duel
                tournamentManager.handleDuelEnd(other, null);
            }
        }

        if (leaver != null && leaver.isOnline()) {
            cleanupDuel(leaver);
        }

        player1 = null;
        player2 = null;
    }

    public boolean isPlaying(Player player) {
        return duelActive && (player.equals(player1) || player.equals(player2));
    }

    public BossBar getBossBar(Player player) {
        if (player.equals(player1))
            return p1Bar;
        if (player.equals(player2))
            return p2Bar;
        return null;
    }

    public boolean isAwaitingGo(Player player) {
        return duelActive && !awaitingJump; // Frozen during countdown (before "GO!")
    }

    public boolean isDuelActive() {
        return duelActive;
    }

    private void spawnBulletTrail(Location start, Location end) {
        org.bukkit.util.Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        direction.normalize();

        for (double d = 0; d <= distance; d += 0.2) {
            Location point = start.clone().add(direction.clone().multiply(d));
            start.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, point, 1, 0, 0, 0, 0);
        }
    }
}
