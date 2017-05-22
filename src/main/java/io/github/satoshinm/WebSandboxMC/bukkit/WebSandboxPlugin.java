
package io.github.satoshinm.WebSandboxMC.bukkit;

import io.github.satoshinm.WebSandboxMC.Settings;
import io.github.satoshinm.WebSandboxMC.bridge.BlockBridge;
import io.github.satoshinm.WebSandboxMC.bridge.PlayersBridge;
import io.github.satoshinm.WebSandboxMC.bridge.WebPlayerBridge;
import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

    @Override
    public void onDisable() {
        webSocketServerThread.webPlayerBridge.deleteAllEntities();

        webSocketServerThread.interrupt();
    }

    @Override
    public void onEnable() {
        final Settings settings = new SettingsBukkit(this);

        checkUnbind(settings);

        final Plugin plugin = this;

        // Run in a delayed task to ensure all worlds are loaded on startup (not only load: POSTWORLD).
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
            @Override
            public void run() {

                webSocketServerThread = new WebSocketServerThread(settings);

                webSocketServerThread.blockBridge = new BlockBridge(webSocketServerThread, settings);
                webSocketServerThread.playersBridge = new PlayersBridge(webSocketServerThread, settings);
                webSocketServerThread.webPlayerBridge = new WebPlayerBridge(webSocketServerThread, settings);

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

    private void checkUnbind(Settings settings) {
        if (!settings.takeover) {
            return;
        }

        if (settings.unbindMethod == null || settings.unbindMethod.equals("")) {
            getLogger().log(Level.WARNING, "Port takeover is enabled but unbind_method is not set; ignoring");
            return;
        }

        Server server = Bukkit.getServer();

        getLogger().log(Level.INFO, "Squatting on port "+server.getPort()+" for server and web, trying unbind: "+settings.unbindMethod);

        String[] array = settings.unbindMethod.split("[.]");
        if (array.length != 3) {
            getLogger().log(Level.WARNING, "Bad 'unbind' option set to: "+settings.unbindMethod+", see source for details.");
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
