package com.parlagames.shooter;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

public class PlayerListener implements Listener {

    private final FastShooterPlugin plugin;

    public PlayerListener(FastShooterPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        TournamentManager tm = plugin.getTournamentManager();

        if (tm.getState() == TournamentManager.State.RUNNING && tm.getDuelManager().isPlaying(player)) {
            // Cancel jump movement entirely during duel
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        TournamentManager tm = plugin.getTournamentManager();

        if (tm.getState() == TournamentManager.State.RUNNING && tm.getDuelManager().isPlaying(player)) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                // Validate they are holding the special gun item
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item.getType() == plugin.getConfigManager().getWeaponMaterial() &&
                        item.hasItemMeta() && item.getItemMeta().hasCustomModelData() &&
                        item.getItemMeta().getCustomModelData() == plugin.getConfigManager().getWeaponData()) {

                    long time = System.currentTimeMillis();
                    tm.getDuelManager().handleShoot(player, time);
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Player player = event.getPlayer();
        TournamentManager tm = plugin.getTournamentManager();

        if (tm.getState() == TournamentManager.State.RUNNING && tm.getDuelManager().isPlaying(player)) {
            if (tm.getDuelManager().isAwaitingGo(player)) {
                org.bukkit.Location from = event.getFrom();
                org.bukkit.Location to = event.getTo();
                if (to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()
                        || from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch())) {
                    event.setTo(from); // Completely freeze X, Y, Z, Yaw, Pitch
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            TournamentManager tm = plugin.getTournamentManager();
            if (tm.getState() == TournamentManager.State.RUNNING && tm.getDuelManager().isPlaying(player)) {
                event.setCancelled(true); // Prevent damage/knockback in duel
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        TournamentManager tm = plugin.getTournamentManager();
        if (tm.hasMemory(player)) {
            tm.saveToPDC(player); // Save to playerdata before quit
        }
        tm.leaveQueue(player);
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getTournamentManager().checkAndRestoreFromPDC(player);
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        TournamentManager tm = plugin.getTournamentManager();
        if (tm.hasMemory(player)) {
            tm.saveToPDC(player); // Save to playerdata before quit
        }
        tm.leaveQueue(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            TournamentManager tm = plugin.getTournamentManager();
            if (tm.getState() == TournamentManager.State.RUNNING && tm.getDuelManager().isPlaying(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemHeldChange(org.bukkit.event.player.PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        TournamentManager tm = plugin.getTournamentManager();
        if (tm.getState() == TournamentManager.State.RUNNING && tm.getDuelManager().isPlaying(player)) {
            event.setCancelled(true); // Lock slot
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        TournamentManager tm = plugin.getTournamentManager();

        if (tm.getState() == TournamentManager.State.RUNNING && tm.getDuelManager().isPlaying(player)) {
            // Allow them to leave
            if (event.getMessage().toLowerCase().startsWith("/shooter leave")
                    || event.getMessage().toLowerCase().startsWith("/qd leave")
                    || event.getMessage().toLowerCase().startsWith("/fs leave")) {
                return;
            }
            // Disallow setup
            if (player.hasPermission("shooter.admin.setup")) {
                return;
            }

            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
        }
    }
}
