
package io.github.satoshinm.WebSandboxMC.bukkit;

import io.github.satoshinm.WebSandboxMC.Settings;
import io.github.satoshinm.WebSandboxMC.bridge.BlockBridge;
import io.github.satoshinm.WebSandboxMC.bridge.PlayersBridge;
import io.github.satoshinm.WebSandboxMC.bridge.WebPlayerBridge;
import io.github.satoshinm.WebSandboxMC.bukkit.BlockListener;
import io.github.satoshinm.WebSandboxMC.bukkit.EntityListener;
import io.github.satoshinm.WebSandboxMC.bukkit.PlayersListener;
import io.github.satoshinm.WebSandboxMC.bukkit.WsCommand;
import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Bukkit plugin class for WebSandboxMC
 *
 * Based on: https://github.com/bukkit/SamplePlugin/
 *
 * Sample plugin for Bukkit
 *
 * @author Dinnerbone
 */
public class WebSandboxPlugin extends JavaPlugin {
    private WebSocketServerThread webSocketServerThread;

    final Settings settings = new Settings();

    @Override
    public void onDisable() {
        webSocketServerThread.webPlayerBridge.deleteAllEntities();

        webSocketServerThread.interrupt();
    }

    @Override
    public void onEnable() {
        // Configuration
        final FileConfiguration config = getConfig();
        config.options().copyDefaults(true);


        config.addDefault("http.port", settings.httpPort);
        config.addDefault("http.takeover", settings.takeover);
        config.addDefault("http.unbind_method", settings.unbindMethod);

        config.addDefault("mc.debug", settings.debug);
        config.addDefault("mc.use_permissions", settings.usePermissions);
        config.addDefault("mc.entity", settings.entityClassName);
        config.addDefault("mc.entity_custom_names", settings.setCustomNames);
        config.addDefault("mc.entity_disable_gravity", settings.disableGravity);
        config.addDefault("mc.entity_disable_ai", settings.disableAI);
        config.addDefault("mc.entity_move_sandbox", settings.entityMoveSandbox);
        config.addDefault("mc.entity_die_disconnect", settings.entityDieDisconnect);
        config.addDefault("mc.world", settings.world);
        config.addDefault("mc.x_center", settings.x_center);
        config.addDefault("mc.y_center", settings.y_center);
        config.addDefault("mc.z_center", settings.z_center);
        config.addDefault("mc.radius", settings.radius);

        config.addDefault("nc.y_offset", settings.y_offset);
        config.addDefault("nc.allow_break_place_blocks", settings.allowBreakPlaceBlocks);
        settings.unbreakableBlocks.add("BEDROCK");
        config.addDefault("nc.unbreakable_blocks", settings.unbreakableBlocks);
        config.addDefault("nc.allow_signs", settings.allowSigns);
        config.addDefault("nc.allow_chatting", settings.allowChatting);
        config.addDefault("nc.see_chat", settings.seeChat);
        config.addDefault("nc.see_players", settings.seePlayers);

        config.addDefault("nc.blocks_to_web_override", settings.blocksToWebOverride);
        config.addDefault("nc.warn_missing_blocks_to_web", settings.warnMissing);

        settings.httpPort = this.getConfig().getInt("http.port");
        settings.takeover = this.getConfig().getBoolean("http.takeover");
        settings.unbindMethod = this.getConfig().getString("http.unbind_method");

        settings.debug = this.getConfig().getBoolean("mc.debug");
        settings.usePermissions = this.getConfig().getBoolean("mc.use_permissions");

        settings.entityClassName = this.getConfig().getString("mc.entity");
        settings.setCustomNames = this.getConfig().getBoolean("mc.entity_custom_names");
        settings.disableGravity = this.getConfig().getBoolean("mc.entity_disable_gravity");
        settings.disableAI = this.getConfig().getBoolean("mc.entity_disable_ai");
        settings.entityMoveSandbox = this.getConfig().getBoolean("mc.entity_move_sandbox");
        settings.entityDieDisconnect = this.getConfig().getBoolean("mc.entity_die_disconnect");

        settings.world = this.getConfig().getString("mc.world");
        settings.x_center = this.getConfig().getInt("mc.x_center");
        settings.y_center = this.getConfig().getInt("mc.y_center");
        settings.z_center = this.getConfig().getInt("mc.z_center");
        settings.radius = this.getConfig().getInt("mc.radius");

        settings.y_offset = this.getConfig().getInt("nc.y_offset");

        settings.allowBreakPlaceBlocks = this.getConfig().getBoolean("nc.allow_break_place_blocks");
        settings.unbreakableBlocks = this.getConfig().getStringList("nc.unbreakable_blocks");
        settings.allowSigns = this.getConfig().getBoolean("nc.allow_signs");
        settings.allowChatting = this.getConfig().getBoolean("nc.allow_chatting");
        settings.seeChat = this.getConfig().getBoolean("nc.see_chat");
        settings.seePlayers = this.getConfig().getBoolean("nc.see_players");
        if (this.getConfig().getConfigurationSection("nc.blocks_to_web") != null) {
            getLogger().log(Level.WARNING, "blocks_to_web is now ignored, you can remove it or add to blocks_to_web_override instead");
        }

        ConfigurationSection section = this.getConfig().getConfigurationSection("nc.blocks_to_web_override");
        if (section != null) {
            for (Map.Entry<String, Object> entry : section.getValues(false).entrySet()) {
                settings.blocksToWebOverride.put(entry.getKey(), entry.getValue());
            }
        }
        settings.warnMissing = this.getConfig().getBoolean("nc.warn_missing_blocks_to_web");
        File file = new File(this.getDataFolder(), "textures.zip");
        if (file.exists()) {
            //textureURL = this.getConfig().getString("nc.texture_url");
            // Although arbitrary URLs could be configured, due to access control checks this becomes confusing, so
            // only allow auto-configuring as this special case to connect back to ourselves in /textures.zip.
            settings.textureURL = "-";
        }

        saveConfig();

        checkUnbind(settings.takeover, settings.unbindMethod);

        final Plugin plugin = this;

        // Run in a delayed task to ensure all worlds are loaded on startup (not only load: POSTWORLD).
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
            @Override
            public void run() {

                webSocketServerThread = new WebSocketServerThread(plugin, settings.httpPort, settings.debug);

                webSocketServerThread.blockBridge = new BlockBridge(webSocketServerThread,
                        settings.world, settings.x_center, settings.y_center,
                        settings.z_center, settings.radius, settings.y_offset,
                        settings.allowBreakPlaceBlocks, settings.allowSigns, settings.blocksToWebOverride,
                        settings.warnMissing, settings.unbreakableBlocks, settings.textureURL);
                webSocketServerThread.playersBridge = new PlayersBridge(webSocketServerThread, settings.allowChatting,
                        settings.seeChat, settings.seePlayers);
                webSocketServerThread.webPlayerBridge = new WebPlayerBridge(webSocketServerThread,
                        settings.setCustomNames, settings.disableGravity, settings.disableAI, settings.entityClassName,
                        settings.entityMoveSandbox, settings.entityDieDisconnect);

                // Register our events
                PluginManager pm = getServer().getPluginManager();

                pm.registerEvents(new BlockListener(webSocketServerThread.blockBridge), plugin);
                pm.registerEvents(new PlayersListener(webSocketServerThread.playersBridge), plugin);
                pm.registerEvents(new EntityListener(webSocketServerThread.webPlayerBridge), plugin);

                // Register our commands
                getCommand("websandbox").setExecutor(new WsCommand(webSocketServerThread, settings.usePermissions));

                // Run the websocket server
                webSocketServerThread.start();
            }
        });
    }

    private void checkUnbind(boolean takeover, String unbind) {
        if (!takeover) {
            return;
        }

        if (unbind == null || unbind.equals("")) {
            getLogger().log(Level.WARNING, "Port takeover is enabled but unbind_method is not set; ignoring");
            return;
        }

        Server server = Bukkit.getServer();

        getLogger().log(Level.INFO, "Squatting on port "+server.getPort()+" for server and web, trying unbind: "+unbind);

        String[] array = unbind.split("[.]");
        if (array.length != 3) {
            getLogger().log(Level.WARNING, "Bad 'unbind' option set to: "+unbind+", see source for details.");
            return; // ignore it, they can read this source below for the format
        }

        // Reuse same port as Bukkit, repurposing it for our purposes
        settings.httpPort = server.getPort();

        // Format is "field1Name.method2Name.method3Name", called on Bukkit.getServer() before startup
        String field1Name = array[0];
        String method2Name = array[1];
        String method3Name = array[2];

        // First, "unbind" the previous port
        try {
            Field field = server.getClass().getDeclaredField(field1Name);
            field.setAccessible(true);
            Object console = field.get(server);

            Method method1 = console.getClass().getMethod(method2Name);
            Object object2 = method1.invoke(console);

            getLogger().log(Level.INFO, "Unbind server port...");
            Method method2 = object2.getClass().getMethod(method3Name);
            method2.invoke(object2);

        } catch (NoSuchFieldException ex) {
            ex.printStackTrace();
        } catch (IllegalAccessException ex) {
            ex.printStackTrace();
        } catch (NoSuchMethodException ex) {
            ex.printStackTrace();
        } catch (InvocationTargetException ex) {
            ex.printStackTrace();
        }
    }
}
