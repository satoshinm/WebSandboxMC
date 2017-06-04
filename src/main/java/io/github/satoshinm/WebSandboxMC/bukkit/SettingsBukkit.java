package io.github.satoshinm.WebSandboxMC.bukkit;

import io.github.satoshinm.WebSandboxMC.Settings;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Map;
import java.util.logging.Level;

public class SettingsBukkit extends Settings {
    private Plugin plugin;

    public SettingsBukkit(Plugin plugin) {
        this.plugin = plugin;

        // Configuration
        final FileConfiguration config = plugin.getConfig();
        config.options().copyDefaults(true);

        config.addDefault("http.port", this.httpPort);
        config.addDefault("http.publicURL", this.publicURL);
        config.addDefault("http.takeover", this.takeover);
        config.addDefault("http.unbind_method", this.unbindMethod);

        config.addDefault("mc.debug", this.debug);
        config.addDefault("mc.netty_log_info", this.nettyLogInfo);
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
        config.addDefault("mc.clickable_links", this.clickableLinks);
        config.addDefault("mc.clickable_links_tellraw", this.clickableLinksTellraw);

        config.addDefault("nc.y_offset", this.y_offset);
        config.addDefault("nc.allow_anonymous", this.allowAnonymous);
        config.addDefault("nc.allow_break_place_blocks", this.allowBreakPlaceBlocks);
        this.unbreakableBlocks.add("BEDROCK");
        config.addDefault("nc.unbreakable_blocks", this.unbreakableBlocks);
        config.addDefault("nc.allow_signs", this.allowSigns);
        config.addDefault("nc.allow_chatting", this.allowChatting);
        config.addDefault("nc.see_chat", this.seeChat);
        config.addDefault("nc.see_players", this.seePlayers);
        config.addDefault("nc.creative_mode", this.creativeMode);

        config.addDefault("nc.blocks_to_web_override", this.blocksToWebOverride);
        config.addDefault("nc.warn_missing_blocks_to_web", this.warnMissing);

        this.httpPort = plugin.getConfig().getInt("http.port");
        this.publicURL = plugin.getConfig().getString("http.publicURL");
        this.takeover = plugin.getConfig().getBoolean("http.takeover");
        this.unbindMethod = plugin.getConfig().getString("http.unbind_method");

        this.debug = plugin.getConfig().getBoolean("mc.debug");
        this.nettyLogInfo = plugin.getConfig().getBoolean("mc.netty_log_info");
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

        this.clickableLinks = plugin.getConfig().getBoolean("mc.clickable_links");
        this.clickableLinksTellraw = plugin.getConfig().getBoolean("mc.clickable_links_tellraw");

        this.y_offset = plugin.getConfig().getInt("nc.y_offset");

        this.allowAnonymous = plugin.getConfig().getBoolean("nc.allow_anonymous");
        this.allowBreakPlaceBlocks = plugin.getConfig().getBoolean("nc.allow_break_place_blocks");
        this.unbreakableBlocks = plugin.getConfig().getStringList("nc.unbreakable_blocks");
        this.allowSigns = plugin.getConfig().getBoolean("nc.allow_signs");
        this.allowChatting = plugin.getConfig().getBoolean("nc.allow_chatting");
        this.seeChat = plugin.getConfig().getBoolean("nc.see_chat");
        this.seePlayers = plugin.getConfig().getBoolean("nc.see_players");
        this.creativeMode = plugin.getConfig().getBoolean("nc.creative_mode");
        if (plugin.getConfig().getConfigurationSection("nc.blocks_to_web") != null) {
            this.log(Level.WARNING, "blocks_to_web is now ignored, you can remove it or add to blocks_to_web_override instead");
        }

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("nc.blocks_to_web_override");
        if (section != null) {
            for (Map.Entry<String, Object> entry : section.getValues(false).entrySet()) {
                this.blocksToWebOverride.put(entry.getKey(), entry.getValue());
            }
        }
        this.warnMissing = plugin.getConfig().getBoolean("nc.warn_missing_blocks_to_web");

        this.pluginDataFolder = plugin.getDataFolder();
        File file = new File(this.pluginDataFolder, "textures.zip");
        if (file.exists()) {
            //textureURL = plugin.getConfig().getString("nc.texture_url");
            // Although arbitrary URLs could be configured, due to access control checks this becomes confusing, so
            // only allow auto-configuring as this special case to connect back to ourselves in /textures.zip.
            this.textureURL = "-";
        }

        plugin.saveConfig();
    }

    public void log(Level level, String message) {
        if (level == Level.FINEST && !debug) {
            return;
        }
        plugin.getLogger().log(level, message);
    }

    public void scheduleSyncTask(Runnable runnable) {
        if (!plugin.isEnabled()) {
            // When we are shutting down, the Netty channels go inactive, but we cannot schedule tasks when
            // the plugin is disabled so just return.
            return;
        }
        Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, runnable);
    }
}
