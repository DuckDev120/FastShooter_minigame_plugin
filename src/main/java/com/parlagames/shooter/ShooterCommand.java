package com.parlagames.shooter;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ShooterCommand implements CommandExecutor, TabCompleter {

    private final FastShooterPlugin plugin;

    public ShooterCommand(FastShooterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no_console"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        TournamentManager tm = plugin.getTournamentManager();
        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "join":
                if (!player.hasPermission("shooter.player.join")) {
                    breakPerm(player);
                    return true;
                }
                tm.joinQueue(player);
                break;

            case "leave":
                tm.leaveQueue(player);
                break;

            case "help":
                sendHelp(player);
                break;

            case "reload":
                if (!player.hasPermission("shooter.admin.reload")) {
                    breakPerm(player);
                } else {
                    if (tm.getState() == TournamentManager.State.RUNNING) {
                        player.sendMessage(plugin.getConfigManager().getMessage("tournament_running_noreload"));
                    } else {
                        plugin.getConfigManager().reload();
                        player.sendMessage(plugin.getConfigManager().getMessage("reload_success"));
                    }
                }
                break;

            case "on":
                if (!player.hasPermission("shooter.admin.state"))
                    breakPerm(player);
                else {
                    if (tm.getState() != TournamentManager.State.IDLE
                            && tm.getState() != TournamentManager.State.CLOSED) {
                        player.sendMessage(plugin.getConfigManager().getMessage("tournament_already_open"));
                    } else {
                        tm.setTournamentState(TournamentManager.State.OPEN);
                        org.bukkit.Bukkit.broadcast(plugin.getConfigManager().getMessage("queue_opened"));
                    }
                }
                break;

            case "off":
                if (!player.hasPermission("shooter.admin.state"))
                    breakPerm(player);
                else {
                    if (tm.getState() == TournamentManager.State.OPEN) {
                        tm.setTournamentState(TournamentManager.State.CLOSED);
                        org.bukkit.Bukkit.broadcast(plugin.getConfigManager().getMessage("queue_closed"));
                    } else {
                        player.sendMessage(plugin.getConfigManager().getMessage("tournament_closed")); // Or already
                                                                                                       // closed
                    }
                }
                break;

            case "start":
                if (!player.hasPermission("shooter.admin.state"))
                    breakPerm(player);
                else {
                    if (tm.getState() == TournamentManager.State.RUNNING) {
                        player.sendMessage(plugin.getConfigManager().getMessage("tournament_already_started"));
                    } else {
                        ConfigManager cfg = plugin.getConfigManager();
                        if (cfg.getLocation("pos1") == null || cfg.getLocation("pos2") == null ||
                                cfg.getLocation("shoot1") == null || cfg.getLocation("shoot2") == null) {
                            player.sendMessage(cfg.getMessage("msg_not_enough_locations"));
                            break;
                        }
                        tm.startTournament();
                        if (tm.getState() != TournamentManager.State.RUNNING) {
                            player.sendMessage(plugin.getConfigManager().getMessage("not_enough_players"));
                        }
                    }
                }
                break;

            case "stop":
                if (!player.hasPermission("shooter.admin.state"))
                    breakPerm(player);
                else {
                    tm.forceStopTournament();
                    org.bukkit.Bukkit.broadcast(plugin.getConfigManager().getMessage("tournament_stopped"));
                }
                break;

            case "setup":
                if (!player.hasPermission("shooter.admin.setup"))
                    breakPerm(player);
                else {
                    if (args.length < 2) {
                        player.sendMessage(
                                plugin.getConfigManager().getMessage("prefix").append(net.kyori.adventure.text.Component
                                        .text("Usage: /shooter setup <pos1|pos2|shoot1|shoot2>")));
                        return true;
                    }
                    String locId = args[1].toLowerCase();
                    if (locId.equals("pos1") || locId.equals("pos2") || locId.equals("shoot1")
                            || locId.equals("shoot2")) {
                        plugin.getConfigManager().setLocation(locId, player.getLocation());
                        player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders("setup_success",
                                "location_id", locId));
                    } else {
                        player.sendMessage(
                                plugin.getConfigManager().getMessage("prefix").append(net.kyori.adventure.text.Component
                                        .text("Invalid location! Use pos1, pos2, shoot1, shoot2.")));
                    }
                }
                break;

            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();
        Player player = sender instanceof Player ? (Player) sender : null;
        if (player == null)
            return suggestions;

        if (args.length == 1) {
            String current = args[0].toLowerCase();
            List<String> subCmds = new ArrayList<>(Arrays.asList("join", "leave", "help"));

            if (player.hasPermission("shooter.admin.state")) {
                subCmds.addAll(Arrays.asList("on", "off", "start", "stop"));
            }
            if (player.hasPermission("shooter.admin.setup")) {
                subCmds.add("setup");
            }
            if (player.hasPermission("shooter.admin.reload")) {
                subCmds.add("reload");
            }

            for (String sub : subCmds) {
                if (sub.startsWith(current)) {
                    suggestions.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setup")
                && player.hasPermission("shooter.admin.setup")) {
            String current = args[1].toLowerCase();
            List<String> points = Arrays.asList("pos1", "pos2", "shoot1", "shoot2");
            for (String p : points) {
                if (p.startsWith(current)) {
                    suggestions.add(p);
                }
            }
        }
        return suggestions;
    }

    private void breakPerm(Player player) {
        player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
    }

    private void sendHelp(Player player) {
        MiniMessage mm = MiniMessage.miniMessage();
        player.sendMessage(mm.deserialize("<color:#FFA500><bold>[FastShooter]</bold></color> <gray>Help Menu:</gray>"));
        player.sendMessage(mm.deserialize(
                "<yellow>/fs join</yellow> <dark_gray>-</dark_gray> <white>Join the tournament queue.</white>"));
        player.sendMessage(mm.deserialize(
                "<yellow>/fs leave</yellow> <dark_gray>-</dark_gray> <white>Leave the tournament.</white>"));
        player.sendMessage(mm
                .deserialize("<yellow>/fs help</yellow> <dark_gray>-</dark_gray> <white>Show this help menu.</white>"));

        if (player.hasPermission("shooter.admin.state")) {
            player.sendMessage(mm.deserialize(
                    "<yellow>/fs on|off</yellow> <dark_gray>-</dark_gray> <white>Open or close queue.</white>"));
            player.sendMessage(mm.deserialize(
                    "<yellow>/fs start|stop</yellow> <dark_gray>-</dark_gray> <white>Start or stop tournament.</white>"));
        }
        if (player.hasPermission("shooter.admin.setup")) {
            player.sendMessage(mm.deserialize(
                    "<yellow>/fs setup <point></yellow> <dark_gray>-</dark_gray> <white>Set arena locations (Admin).</white>"));
        }
        if (player.hasPermission("shooter.admin.reload")) {
            player.sendMessage(mm.deserialize(
                    "<yellow>/fs reload</yellow> <dark_gray>-</dark_gray> <white>Reload the configuration.</white>"));
        }
    }
}
