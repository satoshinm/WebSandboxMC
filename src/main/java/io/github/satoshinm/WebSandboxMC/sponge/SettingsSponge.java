package io.github.satoshinm.WebSandboxMC.sponge;

import com.google.common.reflect.TypeToken;
import io.github.satoshinm.WebSandboxMC.Settings;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.scheduler.Task;

import java.io.IOException;
import java.util.logging.Level;

public class SettingsSponge extends Settings {

    private WebSandboxSpongePlugin plugin;

    public SettingsSponge(WebSandboxSpongePlugin plugin) {
        this.plugin = plugin;


        ConfigurationLoader<CommentedConfigurationNode> loader =
                HoconConfigurationLoader.builder().setPath(plugin.defaultConfig).build();
        ConfigurationNode rootNode;
        try {
            rootNode = loader.load();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        this.httpPort = rootNode.getNode("http", "port").getInt(this.httpPort);
        this.takeover = rootNode.getNode("http", "takeover").getBoolean(this.takeover);
        this.unbindMethod = rootNode.getNode("http", "unbind_method").getString(this.unbindMethod);

        this.debug = rootNode.getNode("mc", "debug").getBoolean(this.debug);
        this.usePermissions = rootNode.getNode("mc", "use_permissions").getBoolean(this.usePermissions);

        this.entityClassName = rootNode.getNode("mc", "entity").getString(this.entityClassName);
        this.setCustomNames = rootNode.getNode("mc", "entity_custom_names").getBoolean(this.setCustomNames);
        this.disableGravity = rootNode.getNode("mc", "entity_disable_gravity").getBoolean(this.disableGravity);
        this.disableAI = rootNode.getNode("mc", "entity_disable_ai").getBoolean(this.disableAI);
        this.entityMoveSandbox = rootNode.getNode("mc", "entity_move_sandbox").getBoolean(this.entityMoveSandbox);
        this.entityDieDisconnect = rootNode.getNode("mc", "entity_die_disconnect").getBoolean(this.entityDieDisconnect);

        this.world = rootNode.getNode("mc", "world").getString(this.world);
        this.x_center = rootNode.getNode("mc", "x_center").getInt(this.x_center);
        this.y_center = rootNode.getNode("mc", "y_center").getInt(this.y_center);
        this.z_center = rootNode.getNode("mc", "z_center").getInt(this.z_center);
        this.radius = rootNode.getNode("mc", "radius").getInt(this.radius);

        this.y_offset = rootNode.getNode("nc", "y_offset").getInt(this.y_offset);

        this.allowBreakPlaceBlocks = rootNode.getNode("nc", "allow_break_place_blocks").getBoolean(this.allowBreakPlaceBlocks);
        try {
            this.unbreakableBlocks = rootNode.getNode("nc", "unbreakable_blocks").getList(TypeToken.of(String.class));
        } catch (ObjectMappingException ex) {
            ex.printStackTrace();
        }
        this.allowSigns = rootNode.getNode("nc", "allow_signs").getBoolean(this.allowSigns);
        this.allowChatting = rootNode.getNode("nc", "allow_chatting").getBoolean(this.allowChatting);
        this.seeChat = rootNode.getNode("nc", "see_chat").getBoolean(this.seeChat);
        this.seePlayers = rootNode.getNode("nc", "see_players").getBoolean(this.seePlayers);

        log(Level.INFO, "debug? " + this.debug);

        try {
            plugin.configManager.save(rootNode);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void log(Level level, String message) {
        if (level == Level.FINEST && !debug) {
            return;
        }

        if (level == Level.FINEST) {
            plugin.logger.debug(message);
        } else if (level == Level.WARNING) {
            plugin.logger.warn(message);
        } else {
            plugin.logger.info(message);
        }
    }

    @Override
    public void scheduleSyncTask(Runnable runnable) {
        //TODO: Task.builder().
    }
}
