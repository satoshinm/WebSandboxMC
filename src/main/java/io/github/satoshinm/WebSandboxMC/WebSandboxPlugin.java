
package io.github.satoshinm.WebSandboxMC;

import java.util.HashMap;

import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Sample plugin for Bukkit
 *
 * @author Dinnerbone
 */
public class WebSandboxPlugin extends JavaPlugin {
    private final SamplePlayerListener playerListener = new SamplePlayerListener(this);
    private final SampleBlockListener blockListener = new SampleBlockListener();
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
        // TODO: Place any custom enable code here including the registration of any events

        // Register our events
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(playerListener, this);
        pm.registerEvents(blockListener, this);

        // Register our commands
        getCommand("pos").setExecutor(new SamplePosCommand());
        getCommand("debug").setExecutor(new SampleDebugCommand(this));

        // Configuration
        final FileConfiguration config = getConfig();
        config.options().copyDefaults(true);

        int httpPort = 4081;

        config.addDefault("http.port", httpPort);

        httpPort = this.getConfig().getInt("http.port");

        saveConfig();

        // Run the websocket server
        webSocketServerThread = new WebSocketServerThread(httpPort);
        webSocketServerThread.start();

        // EXAMPLE: Custom code, here we just output some info so we can check all is well
        PluginDescriptionFile pdfFile = this.getDescription();
        getLogger().info( pdfFile.getName() + " version " + pdfFile.getVersion() + " is enabled!" );
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
