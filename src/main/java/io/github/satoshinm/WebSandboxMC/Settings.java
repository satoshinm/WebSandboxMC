package io.github.satoshinm.WebSandboxMC;

import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class Settings {
    public int httpPort = 4081;
    public boolean takeover = false;
    public String unbindMethod = "console.getServerConnection.b";

    public boolean debug = false;
    public boolean usePermissions = false;
    public String entityClassName = "Sheep";
    public boolean setCustomNames = true;
    public boolean disableGravity = true;
    public boolean disableAI = true;
    public boolean entityMoveSandbox = true;
    public boolean entityDieDisconnect = false;

    // Send blocks around this area in the Bukkit world
    public String world = "";
    public int x_center = 0;
    public int y_center = 75;
    public int z_center = 0;

    // of this radius, +/-
    public int radius = 16;

    // raised this amount in the web world, so it is clearly distinguished from the client-generated terrain
    public int y_offset = 20;

    public boolean allowBreakPlaceBlocks = true;
    public List<String> unbreakableBlocks = new ArrayList<String>();
    public boolean allowSigns = true;
    public boolean allowChatting = true;
    public boolean seeChat = true;
    public boolean seePlayers = true;

    public Map<String, Object> blocksToWebOverride = new HashMap<String, Object>();
    public boolean warnMissing = true;
    public String textureURL = null;

    public void loadBukkitConfig(Plugin plugin) {
        final FileConfiguration config = plugin.getConfig();
        config.options().copyDefaults(true);

        config.addDefault("http.port", this.httpPort);
        config.addDefault("http.takeover", this.takeover);
        config.addDefault("http.unbind_method", this.unbindMethod);

        config.addDefault("mc.debug", this.debug);
        config.addDefault("mc.use_permissions", this.usePermissions);
        config.addDefault("mc.entity", this.entityClassName);
        config.addDefault("mc.entity_custom_names", this.setCustomNames);
        config.addDefault("mc.entity_disable_gravity", this.disableGravity);
        config.addDefault("mc.entity_disable_ai", this.disableAI);
        config.addDefault("mc.entity_move_sandbox", this.entityMoveSandbox);
        config.addDefault("mc.entity_die_disconnect", this.entityDieDisconnect);
        config.addDefault("mc.world", this.world);
        config.addDefault("mc.x_center", this.x_center);
        config.addDefault("mc.y_center", this.y_center);
        config.addDefault("mc.z_center", this.z_center);
        config.addDefault("mc.radius", this.radius);

        config.addDefault("nc.y_offset", this.y_offset);
        config.addDefault("nc.allow_break_place_blocks", this.allowBreakPlaceBlocks);
        this.unbreakableBlocks.add("BEDROCK");
        config.addDefault("nc.unbreakable_blocks", this.unbreakableBlocks);
        config.addDefault("nc.allow_signs", this.allowSigns);
        config.addDefault("nc.allow_chatting", this.allowChatting);
        config.addDefault("nc.see_chat", this.seeChat);
        config.addDefault("nc.see_players", this.seePlayers);

        config.addDefault("nc.blocks_to_web_override", this.blocksToWebOverride);
        config.addDefault("nc.warn_missing_blocks_to_web", this.warnMissing);

        this.httpPort = plugin.getConfig().getInt("http.port");
        this.takeover = plugin.getConfig().getBoolean("http.takeover");
        this.unbindMethod = plugin.getConfig().getString("http.unbind_method");

        this.debug = plugin.getConfig().getBoolean("mc.debug");
        this.usePermissions = plugin.getConfig().getBoolean("mc.use_permissions");

        this.entityClassName = plugin.getConfig().getString("mc.entity");
        this.setCustomNames = plugin.getConfig().getBoolean("mc.entity_custom_names");
        this.disableGravity = plugin.getConfig().getBoolean("mc.entity_disable_gravity");
        this.disableAI = plugin.getConfig().getBoolean("mc.entity_disable_ai");
        this.entityMoveSandbox = plugin.getConfig().getBoolean("mc.entity_move_sandbox");
        this.entityDieDisconnect = plugin.getConfig().getBoolean("mc.entity_die_disconnect");

        this.world = plugin.getConfig().getString("mc.world");
        this.x_center = plugin.getConfig().getInt("mc.x_center");
        this.y_center = plugin.getConfig().getInt("mc.y_center");
        this.z_center = plugin.getConfig().getInt("mc.z_center");
        this.radius = plugin.getConfig().getInt("mc.radius");

        this.y_offset = plugin.getConfig().getInt("nc.y_offset");

        this.allowBreakPlaceBlocks = plugin.getConfig().getBoolean("nc.allow_break_place_blocks");
        this.unbreakableBlocks = plugin.getConfig().getStringList("nc.unbreakable_blocks");
        this.allowSigns = plugin.getConfig().getBoolean("nc.allow_signs");
        this.allowChatting = plugin.getConfig().getBoolean("nc.allow_chatting");
        this.seeChat = plugin.getConfig().getBoolean("nc.see_chat");
        this.seePlayers = plugin.getConfig().getBoolean("nc.see_players");
        if (plugin.getConfig().getConfigurationSection("nc.blocks_to_web") != null) {
            plugin.getLogger().log(Level.WARNING, "blocks_to_web is now ignored, you can remove it or add to blocks_to_web_override instead");
        }

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("nc.blocks_to_web_override");
        if (section != null) {
            for (Map.Entry<String, Object> entry : section.getValues(false).entrySet()) {
                this.blocksToWebOverride.put(entry.getKey(), entry.getValue());
            }
        }
        this.warnMissing = plugin.getConfig().getBoolean("nc.warn_missing_blocks_to_web");
        File file = new File(plugin.getDataFolder(), "textures.zip");
        if (file.exists()) {
            //textureURL = plugin.getConfig().getString("nc.texture_url");
            // Although arbitrary URLs could be configured, due to access control checks plugin.becomes confusing, so
            // only allow auto-configuring as plugin.special case to connect back to ourselves in /textures.zip.
            this.textureURL = "-";
        }

        plugin.saveConfig();
    }
    
    public void loadSpongeConfig(org.slf4j.Logger logger, Path configDir, Path defaultConfig, ConfigurationLoader<CommentedConfigurationNode> configManager) {
        logger.info("config path: " + configDir);

        ConfigurationLoader<CommentedConfigurationNode> loader =
                HoconConfigurationLoader.builder().setPath(defaultConfig).build();
        ConfigurationNode rootNode;
        try {
            rootNode = loader.load();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        this.httpPort = rootNode.getNode("http", "port").getInt(this.httpPort);
        this.takeover = rootNode.getNode("http", "takeover").getBoolean(this.takeover);
        this.unbindMethod = rootNode.getNode("http", "unbind_method").getString(this.unbindMethod);

        this.debug = rootNode.getNode("mc", "debug").getBoolean(this.debug);
        this.usePermissions = rootNode.getNode("mc", "use_permissions").getBoolean(this.usePermissions);

        this.entityClassName = rootNode.getNode("mc", "entity").getString(this.entityClassName);
        this.setCustomNames = rootNode.getNode("mc", "entity_custom_names").getBoolean(this.setCustomNames);
        this.disableGravity = rootNode.getNode("mc", "entity_disable_gravity").getBoolean(this.disableGravity);
        this.disableAI = rootNode.getNode("mc", "entity_disable_ai").getBoolean(this.disableAI);
        this.entityMoveSandbox = rootNode.getNode("mc", "entity_move_sandbox").getBoolean(this.entityMoveSandbox);
        this.entityDieDisconnect = rootNode.getNode("mc", "entity_die_disconnect").getBoolean(this.entityDieDisconnect);

        this.world = rootNode.getNode("mc", "world").getString(this.world);
        this.x_center = rootNode.getNode("mc", "x_center").getInt(this.x_center);
        this.y_center = rootNode.getNode("mc", "y_center").getInt(this.y_center);
        this.z_center = rootNode.getNode("mc", "z_center").getInt(this.z_center);
        this.radius = rootNode.getNode("mc", "radius").getInt(this.radius);

        this.y_offset = rootNode.getNode("nc", "y_offset").getInt(this.y_offset);

        this.allowBreakPlaceBlocks = rootNode.getNode("nc", "allow_break_place_blocks").getBoolean(this.allowBreakPlaceBlocks);
        try {
            this.unbreakableBlocks = rootNode.getNode("nc", "unbreakable_blocks").getList(TypeToken.of(String.class));
        } catch (ObjectMappingException ex) {
            ex.printStackTrace();
        }
        this.allowSigns = rootNode.getNode("nc", "allow_signs").getBoolean(this.allowSigns);
        this.allowChatting = rootNode.getNode("nc", "allow_chatting").getBoolean(this.allowChatting);
        this.seeChat = rootNode.getNode("nc", "see_chat").getBoolean(this.seeChat);
        this.seePlayers = rootNode.getNode("nc", "see_players").getBoolean(this.seePlayers);

        logger.info("debug? " + this.debug);

        try {
            configManager.save(rootNode);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
