package com.parlagames.shooter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConfigManager {

    private final FastShooterPlugin plugin;
    private final MiniMessage miniMessage;

    private File messagesFile;
    private FileConfiguration messagesConfig;

    public ConfigManager(FastShooterPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();

        // Load config.yml
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        // Load messages.yml
        createMessagesConfig();
    }

    private void createMessagesConfig() {
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            messagesFile.getParentFile().mkdirs();
            plugin.saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Default to file in jar if fields are missing
        InputStream defConfigStream = plugin.getResource("messages.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration
                    .loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
            messagesConfig.setDefaults(defConfig);
        }
    }

    public void reload() {
        plugin.reloadConfig();
        createMessagesConfig();
    }

    private String getLanguageString() {
        return plugin.getConfig().getString("language", "EN").toUpperCase();
    }

    public Component parseString(String text) {
        String lang = getLanguageString();
        String prefix = messagesConfig.getString(lang + ".prefix", "");
        text = text.replace("%shooter%", prefix);

        text = text.replace("&0", "<black>").replace("&1", "<dark_blue>").replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>").replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
                .replace("&6", "<gold>").replace("&7", "<gray>").replace("&8", "<dark_gray>")
                .replace("&9", "<blue>").replace("&a", "<green>").replace("&b", "<aqua>")
                .replace("&c", "<red>").replace("&d", "<light_purple>").replace("&e", "<yellow>")
                .replace("&f", "<white>").replace("&k", "<obfuscated>").replace("&l", "<bold>")
                .replace("&m", "<strikethrough>").replace("&n", "<underlined>").replace("&o", "<italic>")
                .replace("&r", "<reset>");
        text = text.replaceAll("&#([a-fA-F0-9]{6})", "<color:#$1>");

        return miniMessage.deserialize(text);
    }

    public Component getMessage(String path) {
        String lang = getLanguageString();
        String rawMessage = messagesConfig.getString(lang + "." + path, "<red>Message missing: " + path + "</red>");
        return parseString(rawMessage);
    }

    public Component getRawMessage(String path) {
        String lang = getLanguageString();
        String rawMessage = messagesConfig.getString(lang + "." + path, "<red>Message missing: " + path + "</red>");
        return parseString(rawMessage);
    }

    public String getRawMessageString(String path) {
        String lang = getLanguageString();
        return messagesConfig.getString(lang + "." + path, "Missing:" + path);
    }

    public Component getMessageWithPlaceholders(String path, String... placeholders) {
        String lang = getLanguageString();
        String rawMessage = messagesConfig.getString(lang + "." + path, "<red>Message missing: " + path + "</red>");

        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                rawMessage = rawMessage.replace("%" + placeholders[i] + "%", placeholders[i + 1]);
            }
        }
        return parseString(rawMessage);
    }

    public Component getRawMessageWithPlaceholders(String path, String... placeholders) {
        String lang = getLanguageString();
        String rawMessage = messagesConfig.getString(lang + "." + path, "<red>Message missing: " + path + "</red>");

        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                rawMessage = rawMessage.replace("%" + placeholders[i] + "%", placeholders[i + 1]);
            }
        }
        return parseString(rawMessage);
    }

    public Location getLocation(String path) {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("locations." + path);
        if (section == null)
            return null;

        String worldName = config.getString("locations.world");
        World world = worldName != null ? Bukkit.getWorld(worldName) : Bukkit.getWorlds().get(0);

        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw");
        float pitch = (float) section.getDouble("pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    public void setLocation(String path, Location loc) {
        FileConfiguration config = plugin.getConfig();
        config.set("locations.world", loc.getWorld().getName());
        config.set("locations." + path + ".x", loc.getX());
        config.set("locations." + path + ".y", loc.getY());
        config.set("locations." + path + ".z", loc.getZ());
        config.set("locations." + path + ".yaw", loc.getYaw());
        config.set("locations." + path + ".pitch", loc.getPitch());
        plugin.saveConfig();
        reload();
    }

    public Material getWeaponMaterial() {
        String matStr = plugin.getConfig().getString("weapon.material", "WOODEN_HOE");
        Material mat = Material.matchMaterial(matStr);
        return mat != null ? mat : Material.WOODEN_HOE;
    }

    public int getWeaponData() {
        return plugin.getConfig().getInt("weapon.custom-model-data", 1);
    }

    public Component getWeaponDisplayName() {
        String name = plugin.getConfig().getString("weapon.display-name",
                "<gradient:#FFD700:#FFA500>Fast Shooter Gun</gradient>");
        return miniMessage.deserialize(name);
    }

    public java.util.List<Component> getWeaponLore() {
        java.util.List<String> loreStrings = plugin.getConfig().getStringList("weapon.lore");
        java.util.List<Component> lore = new java.util.ArrayList<>();
        for (String s : loreStrings) {
            lore.add(miniMessage.deserialize(s));
        }
        return lore;
    }
}
