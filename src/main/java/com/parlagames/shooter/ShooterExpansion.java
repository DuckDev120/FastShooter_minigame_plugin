package com.parlagames.shooter;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;

public class ShooterExpansion extends PlaceholderExpansion {

    private final FastShooterPlugin plugin;

    public ShooterExpansion(FastShooterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "shooter";
    }

    @Override
    public @NotNull String getAuthor() {
        return "FastShooter"; // Avoid changing authors drastically
    }

    @Override
    public @NotNull String getVersion() {
        return "1.2";
    }

    @Override
    public boolean persist() {
        return true; // Needed to prevent unregistering upon PAPI reload
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        TournamentManager tm = plugin.getTournamentManager();

        if (tm.getState() != TournamentManager.State.RUNNING) {
            return plugin.getConfigManager().getRawMessageString("expansion_not_running");
        }

        switch (identifier.toLowerCase()) {
            case "round":
                int roundNum = tm.getCurrentRound();
                return roundNum > 0 ? "Round " + roundNum
                        : plugin.getConfigManager().getRawMessageString("expansion_na");

            case "progress":
                int currentMatch = tm.getCurrentMatchIndex();
                int totalMatches = tm.getTotalMatchesInRound();
                return currentMatch + "/" + totalMatches;

            case "advanced":
                if (tm.getAdvancedList().isEmpty())
                    return plugin.getConfigManager().getRawMessageString("expansion_none");
                return tm.getAdvancedList().stream()
                        .map(uuid -> {
                            Player p = Bukkit.getPlayer(uuid);
                            return p != null ? p.getName()
                                    : plugin.getConfigManager().getRawMessageString("expansion_unknown");
                        })
                        .collect(Collectors.joining(", "));

            case "eliminated":
                if (tm.getEliminatedList().isEmpty())
                    return plugin.getConfigManager().getRawMessageString("expansion_none");
                return tm.getEliminatedList().stream()
                        .map(uuid -> {
                            Player p = Bukkit.getPlayer(uuid);
                            return p != null ? p.getName()
                                    : plugin.getConfigManager().getRawMessageString("expansion_unknown");
                        })
                        .collect(Collectors.joining(", "));

            default:
                return null;
        }
    }
}
