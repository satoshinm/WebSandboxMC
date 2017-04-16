
package io.github.satoshinm.WebSandboxMC;

import io.github.satoshinm.WebSandboxMC.bridge.BlockBridge;
import io.github.satoshinm.WebSandboxMC.bridge.PlayersBridge;
import io.github.satoshinm.WebSandboxMC.bridge.WebPlayerBridge;
import io.github.satoshinm.WebSandboxMC.bukkit.BlockListener;
import io.github.satoshinm.WebSandboxMC.bukkit.PlayersListener;
import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import org.bukkit.configuration.file.FileConfiguration;
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

    @Override
    public void onDisable() {
        // TODO: Place any custom disable code here

        // NOTE: All registered events are automatically unregistered when a plugin is disabled

        // EXAMPLE: Custom code, here we just output some info so we can check all is well
        getLogger().info("Goodbye world!");
    }

    @Override
    public void onEnable() {
        // Configuration
        final FileConfiguration config = getConfig();
        config.options().copyDefaults(true);

        int httpPort = 4081;

        String entityClassName = "Sheep";
        boolean setCustomNames = true;
        boolean disableGravity = true;

        // Send blocks around this area in the Bukkit world
        String world = "";
        int x_center = -85;
        int y_center = 78;
        int z_center = 93;

        // of this radius, +/-
        int radius = 16;

        // raised this amount in the web world, so it is clearly distinguished from the client-generated terrain
        int y_offset = 20;

        String ourExternalAddress = "localhost";
        int ourExternalPort = httpPort;

        boolean allowBreakPlaceBlocks = true;
        boolean allowSigns = true;
        boolean allowChatting = true;
        boolean seeChat = true;
        boolean seePlayers = true;

        config.addDefault("http.port", httpPort);
        config.addDefault("http.external_address", ourExternalAddress);
        config.addDefault("http.external_port", ourExternalPort);

        config.addDefault("mc.entity", entityClassName);
        config.addDefault("mc.entity_custom_names", setCustomNames);
        config.addDefault("mc.entity_disable_gravity", disableGravity);
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
        ourExternalAddress = this.getConfig().getString("http.external_address");
        ourExternalPort = this.getConfig().getInt("http.external_port");

        entityClassName = this.getConfig().getString("mc.entity");
        setCustomNames = this.getConfig().getBoolean("mc.entity_custom_names");
        disableGravity = this.getConfig().getBoolean("mc.entity_disable_gravity");
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

        webSocketServerThread = new WebSocketServerThread(this, httpPort, ourExternalAddress, ourExternalPort);

        webSocketServerThread.blockBridge = new BlockBridge(webSocketServerThread, world, x_center, y_center, z_center, radius, y_offset, allowBreakPlaceBlocks, allowSigns);
        webSocketServerThread.playersBridge = new PlayersBridge(webSocketServerThread, allowChatting, seeChat, seePlayers);
        webSocketServerThread.webPlayerBridge = new WebPlayerBridge(webSocketServerThread, setCustomNames, disableGravity, entityClassName);

        // Register our events
        PluginManager pm = getServer().getPluginManager();

        BlockListener blockListener = new BlockListener(webSocketServerThread.blockBridge);
        pm.registerEvents(blockListener, this);

        PlayersListener playersListener = new PlayersListener(webSocketServerThread.playersBridge);
        pm.registerEvents(playersListener, this);


        // TODO: Register our commands, what do we need?
        //getCommand("websandbox").setExecutor(new WebsandboxCommand());

        // Run the websocket server
        webSocketServerThread.start();
    }
}
