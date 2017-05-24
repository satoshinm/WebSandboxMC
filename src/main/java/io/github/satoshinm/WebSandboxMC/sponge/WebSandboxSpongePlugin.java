package io.github.satoshinm.WebSandboxMC.sponge;

import com.google.inject.Inject;
import io.github.satoshinm.WebSandboxMC.Settings;
import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartingServerEvent;
import org.spongepowered.api.plugin.Plugin;

import java.nio.file.Path;

@Plugin(id = "websandboxmc",
        name = "WebSandboxMC",
        description = "Web-based client providing an interactive glimpse of a part of your server using WebGL/HTML5",
        version = "1.8.0")
public class WebSandboxSpongePlugin {

    @Inject
    public Logger logger;

    @Inject
    @DefaultConfig(sharedRoot = false)
    public Path defaultConfig;

    @Inject
    @DefaultConfig(sharedRoot = false)
    public ConfigurationLoader<CommentedConfigurationNode> configManager;

    @Inject
    @ConfigDir(sharedRoot = false)
    private Path configDir;

    //@Inject
    //private Game game;

    private Settings settings;

    private WebSocketServerThread webSocketServerThread;

    @Listener
    public void onGameInit(GameInitializationEvent event) {
        logger.info("WebSandboxMC/Sponge starting");
        logger.info("config path: " + configDir);

        settings = new SettingsSponge(this);
    }

    @Listener
    public void onServerStarting(GameStartingServerEvent event) {
        // https://docs.spongepowered.org/stable/en/plugin/lifecycle.html
        // "The server instance exists, and worlds are loaded"

        webSocketServerThread = new WebSocketServerThread(settings);

        /* TODO: factor out bukkit
        webSocketServerThread.blockBridge = new BlockBridge(webSocketServerThread, settings);
        webSocketServerThread.playersBridge = new PlayersBridge(webSocketServerThread, settings);
        webSocketServerThread.webPlayerBridge = new WebPlayerBridge(webSocketServerThread, settings);
        */

        /* TODO: write for sponge
        // Register our events
        PluginManager pm = getServer().getPluginManager();

        pm.registerEvents(new BlockListener(webSocketServerThread.blockBridge), plugin);
        pm.registerEvents(new PlayersListener(webSocketServerThread.playersBridge), plugin);
        pm.registerEvents(new EntityListener(webSocketServerThread.webPlayerBridge), plugin);

        // Register our commands
        getCommand("websandbox").setExecutor(new WsCommand(webSocketServerThread, settings.usePermissions));
        */

        // Run the websocket server
        webSocketServerThread.start();
    }
}
