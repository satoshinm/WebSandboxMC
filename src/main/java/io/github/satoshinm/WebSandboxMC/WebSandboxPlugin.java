
package io.github.satoshinm.WebSandboxMC;

import java.util.HashMap;

import io.github.satoshinm.WebSandboxMC.bridge.BlockBridge;
import io.github.satoshinm.WebSandboxMC.bridge.PlayersBridge;
import io.github.satoshinm.WebSandboxMC.bridge.WebPlayerBridge;
import io.github.satoshinm.WebSandboxMC.bukkit.BlockListener;
import io.github.satoshinm.WebSandboxMC.bukkit.PlayersListener;
import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Sample plugin for Bukkit
 *
 * @author Dinnerbone
 */
public class WebSandboxPlugin extends JavaPlugin {
    private final HashMap<Player, Boolean> debugees = new HashMap<Player, Boolean>();

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

        boolean setCustomNames = true;
        boolean disableGravity = true;

        // Send blocks around this area in the Bukkit world
        int x_center = -85;
        int y_center = 78;
        int z_center = 93;

        // of this radius, +/-
        int radius = 16;

        // raised this amount in the web world, so it is clearly distinguished from the client-generated terrain
        int y_offset = 20;

        String ourExternalAddress = "localhost";
        int ourExternalPort = httpPort;

        config.addDefault("http.port", httpPort);
        config.addDefault("http.external_address", ourExternalAddress);
        config.addDefault("http.external_port", ourExternalPort);

        config.addDefault("mc.entity_custom_names", setCustomNames);
        config.addDefault("mc.entity_disable_gravity", disableGravity);
        config.addDefault("mc.x_center", x_center);
        config.addDefault("mc.y_center", y_center);
        config.addDefault("mc.z_center", z_center);
        config.addDefault("mc.radius", radius);

        config.addDefault("nc.y_offset", y_offset);

        
        httpPort = this.getConfig().getInt("http.port");
        ourExternalAddress = this.getConfig().getString("http.external_address");
        ourExternalPort = this.getConfig().getInt("http.external_port");

        setCustomNames = this.getConfig().getBoolean("mc.entity_custom_names");
        disableGravity = this.getConfig().getBoolean("mc.entity_disable_gravity");
        x_center = this.getConfig().getInt("mc.x_center");
        y_center = this.getConfig().getInt("mc.y_center");
        z_center = this.getConfig().getInt("mc.z_center");
        radius = this.getConfig().getInt("mc.radius");

        y_offset = this.getConfig().getInt("nc.y_offset");

        saveConfig();

        webSocketServerThread = new WebSocketServerThread(this, httpPort, ourExternalAddress, ourExternalPort);

        webSocketServerThread.blockBridge = new BlockBridge(webSocketServerThread, x_center, y_center, z_center, radius, y_offset);
        webSocketServerThread.playersBridge = new PlayersBridge(webSocketServerThread);
        webSocketServerThread.webPlayerBridge = new WebPlayerBridge(webSocketServerThread, setCustomNames, disableGravity);

        // Register our events
        PluginManager pm = getServer().getPluginManager();
        //playerListener = new SamplePlayerListener(this); // TODO
        //pm.registerEvents(playerListener, this);

        BlockListener blockListener = new BlockListener(webSocketServerThread.blockBridge);
        pm.registerEvents(blockListener, this);

        PlayersListener playersListener = new PlayersListener(webSocketServerThread.playersBridge);
        pm.registerEvents(playersListener, this);


        // Register our commands
        getCommand("pos").setExecutor(new SamplePosCommand());
        getCommand("debug").setExecutor(new SampleDebugCommand(this));


        // Run the websocket server
        webSocketServerThread.start();
    }

    public boolean isDebugging(final Player player) {
        if (debugees.containsKey(player)) {
            return debugees.get(player);
        } else {
            return false;
        }
    }

    public void setDebugging(final Player player, final boolean value) {
        debugees.put(player, value);
    }
}
