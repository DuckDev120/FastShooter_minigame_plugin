package com.parlagames.shooter;

import org.bukkit.plugin.java.JavaPlugin;

public class FastShooterPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private TournamentManager tournamentManager;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Initialize managers
        this.configManager = new ConfigManager(this);
        this.tournamentManager = new TournamentManager(this);

        // Register commands
        if (getCommand("shooter") != null) {
            ShooterCommand cmd = new ShooterCommand(this);
            getCommand("shooter").setExecutor(cmd);
            getCommand("shooter").setTabCompleter(cmd);
        }

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        // Register PAPI Expansion if available
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ShooterExpansion(this).register();
        }

        getLogger().info("FastShooter Tournament plugin enabled!");
    }

    @Override
    public void onDisable() {
        if (tournamentManager != null) {
            tournamentManager.forceStopTournament();
        }
        getLogger().info("FastShooter plugin disabled! Tournament safely stopped.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public TournamentManager getTournamentManager() {
        return tournamentManager;
    }
}
