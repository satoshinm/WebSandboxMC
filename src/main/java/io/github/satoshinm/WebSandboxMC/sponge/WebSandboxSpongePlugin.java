package io.github.satoshinm.WebSandboxMC.sponge;

import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import io.github.satoshinm.WebSandboxMC.Settings;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Plugin;

import java.io.IOException;
import java.net.URL;
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

        settings.httpPort = rootNode.getNode("http", "port").getInt(settings.httpPort);
        settings.takeover = rootNode.getNode("http", "takeover").getBoolean(settings.takeover);
        settings.unbindMethod = rootNode.getNode("http", "unbind_method").getString(settings.unbindMethod);

        settings.debug = rootNode.getNode("mc", "debug").getBoolean(settings.debug);
        settings.usePermissions = rootNode.getNode("mc", "use_permissions").getBoolean(settings.usePermissions);

        settings.entityClassName = rootNode.getNode("mc", "entity").getString(settings.entityClassName);
        settings.setCustomNames = rootNode.getNode("mc", "entity_custom_names").getBoolean(settings.setCustomNames);
        settings.disableGravity = rootNode.getNode("mc", "entity_disable_gravity").getBoolean(settings.disableGravity);
        settings.disableAI = rootNode.getNode("mc", "entity_disable_ai").getBoolean(settings.disableAI);
        settings.entityMoveSandbox = rootNode.getNode("mc", "entity_move_sandbox").getBoolean(settings.entityMoveSandbox);
        settings.entityDieDisconnect = rootNode.getNode("mc", "entity_die_disconnect").getBoolean(settings.entityDieDisconnect);

        settings.world = rootNode.getNode("mc", "world").getString(settings.world);
        settings.x_center = rootNode.getNode("mc", "x_center").getInt(settings.x_center);
        settings.y_center = rootNode.getNode("mc", "y_center").getInt(settings.y_center);
        settings.z_center = rootNode.getNode("mc", "z_center").getInt(settings.z_center);
        settings.radius = rootNode.getNode("mc", "radius").getInt(settings.radius);

        settings.y_offset = rootNode.getNode("nc", "y_offset").getInt(settings.y_offset);

        settings.allowBreakPlaceBlocks = rootNode.getNode("nc", "allow_break_place_blocks").getBoolean(settings.allowBreakPlaceBlocks);
        try {
            settings.unbreakableBlocks = rootNode.getNode("nc", "unbreakable_blocks").getList(TypeToken.of(String.class));
        } catch (ObjectMappingException ex) {
            ex.printStackTrace();
        }
        settings.allowSigns = rootNode.getNode("nc", "allow_signs").getBoolean(settings.allowSigns);
        settings.allowChatting = rootNode.getNode("nc", "allow_chatting").getBoolean(settings.allowChatting);
        settings.seeChat = rootNode.getNode("nc", "see_chat").getBoolean(settings.seeChat);
        settings.seePlayers = rootNode.getNode("nc", "see_players").getBoolean(settings.seePlayers);

        logger.info("debug? " + settings.debug);

        try {
            configManager.save(rootNode);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
