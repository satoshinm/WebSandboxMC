
package io.github.satoshinm.WebSandboxMC;

import io.github.satoshinm.WebSandboxMC.bridge.BlockBridge;
import io.github.satoshinm.WebSandboxMC.bridge.PlayersBridge;
import io.github.satoshinm.WebSandboxMC.bridge.WebPlayerBridge;
import io.github.satoshinm.WebSandboxMC.bukkit.BlockListener;
import io.github.satoshinm.WebSandboxMC.bukkit.EntityListener;
import io.github.satoshinm.WebSandboxMC.bukkit.PlayersListener;
import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

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

    private int httpPort = 4081;

    private boolean debug = false;
    private String entityClassName = "Sheep";
    private boolean setCustomNames = true;
    private boolean disableGravity = true;
    private boolean disableAI = true;
    private boolean entityMoveSandbox = true;
    private boolean entityDieDisconnect = false;

    // Send blocks around this area in the Bukkit world
    private String world = "";
    private int x_center = -85;
    private int y_center = 78;
    private int z_center = 93;

    // of this radius, +/-
    private int radius = 16;

    // raised this amount in the web world, so it is clearly distinguished from the client-generated terrain
    private int y_offset = 20;

    private boolean allowBreakPlaceBlocks = true;
    private boolean allowSigns = true;
    private boolean allowChatting = true;
    private boolean seeChat = true;
    private boolean seePlayers = true;

    @Override
    public void onDisable() {
        webSocketServerThread.interrupt();
    }

    @Override
    public void onEnable() {
        // Configuration
        final FileConfiguration config = getConfig();
        config.options().copyDefaults(true);

        config.addDefault("http.port", httpPort);

        config.addDefault("mc.debug", debug);
        config.addDefault("mc.entity", entityClassName);
        config.addDefault("mc.entity_custom_names", setCustomNames);
        config.addDefault("mc.entity_disable_gravity", disableGravity);
        config.addDefault("mc.entity_disable_ai", disableAI);
        config.addDefault("mc.entity_move_sandbox", entityMoveSandbox);
        config.addDefault("mc.entity_die_disconnect", entityDieDisconnect);
        config.addDefault("mc.world", world);
        config.addDefault("mc.x_center", x_center);
        config.addDefault("mc.y_center", y_center);
        config.addDefault("mc.z_center", z_center);
        config.addDefault("mc.radius", radius);

        config.addDefault("nc.y_offset", y_offset);
        config.addDefault("nc.allow_break_place_blocks", allowBreakPlaceBlocks);
        config.addDefault("nc.allow_signs", allowSigns);
        config.addDefault("nc.allow_chatting", allowChatting);
        config.addDefault("nc.see_chat", seeChat);
        config.addDefault("nc.see_players", seePlayers);

        
        httpPort = this.getConfig().getInt("http.port");

        debug =  this.getConfig().getBoolean("mc.debug");

        entityClassName = this.getConfig().getString("mc.entity");
        setCustomNames = this.getConfig().getBoolean("mc.entity_custom_names");
        disableGravity = this.getConfig().getBoolean("mc.entity_disable_gravity");
        disableAI = this.getConfig().getBoolean("mc.entity_disable_ai");
        entityMoveSandbox = this.getConfig().getBoolean("mc.entity_move_sandbox");
        entityDieDisconnect = this.getConfig().getBoolean("mc.entity_die_disconnect");

        world = this.getConfig().getString("mc.world");
        x_center = this.getConfig().getInt("mc.x_center");
        y_center = this.getConfig().getInt("mc.y_center");
        z_center = this.getConfig().getInt("mc.z_center");
        radius = this.getConfig().getInt("mc.radius");

        y_offset = this.getConfig().getInt("nc.y_offset");

        allowBreakPlaceBlocks = this.getConfig().getBoolean("nc.allow_break_place_blocks");
        allowSigns = this.getConfig().getBoolean("nc.allow_signs");
        allowChatting = this.getConfig().getBoolean("nc.allow_chatting");
        seeChat = this.getConfig().getBoolean("nc.see_chat");
        seePlayers = this.getConfig().getBoolean("nc.see_players");

        saveConfig();

        final Plugin plugin = this;

        // Run in a delayed task to ensure all worlds are loaded on startup (not only load: POSTWORLD).
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
            @Override
            public void run() {

                webSocketServerThread = new WebSocketServerThread(plugin, httpPort, debug);

                webSocketServerThread.blockBridge = new BlockBridge(webSocketServerThread, world, x_center, y_center, z_center, radius, y_offset, allowBreakPlaceBlocks, allowSigns);
                webSocketServerThread.playersBridge = new PlayersBridge(webSocketServerThread, allowChatting, seeChat, seePlayers);
                webSocketServerThread.webPlayerBridge = new WebPlayerBridge(webSocketServerThread, setCustomNames,
                        disableGravity, disableAI, entityClassName, entityMoveSandbox, entityDieDisconnect);

                // Register our events
                PluginManager pm = getServer().getPluginManager();

                pm.registerEvents(new BlockListener(webSocketServerThread.blockBridge), plugin);
                pm.registerEvents(new PlayersListener(webSocketServerThread.playersBridge), plugin);
                pm.registerEvents(new EntityListener(webSocketServerThread.webPlayerBridge), plugin);

                // TODO: Register our commands, what do we need?
                //getCommand("websandbox").setExecutor(new WebsandboxCommand());

                // Run the websocket server
                webSocketServerThread.start();
            }
        });
    }
}
