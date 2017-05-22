package io.github.satoshinm.WebSandboxMC.sponge;

import com.google.inject.Inject;
import io.github.satoshinm.WebSandboxMC.Settings;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Plugin;

import java.nio.file.Path;

@Plugin(id = "websandboxmc", name = "WebSandboxMC", version = "1.6.0")
public class WebSandboxSpongePlugin {

    @Inject
    private Logger logger;

    @Inject
    @DefaultConfig(sharedRoot = false)
    private Path defaultConfig;

    @Inject
    @DefaultConfig(sharedRoot = false)
    private ConfigurationLoader<CommentedConfigurationNode> configManager;

    @Inject
    @ConfigDir(sharedRoot = false)
    private Path configDir;

    @Inject
    private Game game;

    private Settings settings = new Settings();

    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        logger.info("WebSandboxMC/Sponge starting");

        settings.loadSpongeConfig(logger, configDir, defaultConfig, configManager);
    }
}
