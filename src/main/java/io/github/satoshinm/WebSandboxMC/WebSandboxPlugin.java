
package io.github.satoshinm.WebSandboxMC;

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

    private int httpPort = 4081;
    private boolean takeover = false;
    private String unbindMethod = "console.getServerConnection.b";

    private boolean debug = false;
    private String entityClassName = "Sheep";
    private boolean setCustomNames = true;
    private boolean disableGravity = true;
    private boolean disableAI = true;
    private boolean entityMoveSandbox = true;
    private boolean entityDieDisconnect = false;

    // Send blocks around this area in the Bukkit world
    private String world = "";
    private int x_center = 0;
    private int y_center = 75;
    private int z_center = 0;

    // of this radius, +/-
    private int radius = 16;

    // raised this amount in the web world, so it is clearly distinguished from the client-generated terrain
    private int y_offset = 20;

    private boolean allowBreakPlaceBlocks = true;
    private List<String> unbreakableBlocks = new ArrayList<String>();
    private boolean allowSigns = true;
    private boolean allowChatting = true;
    private boolean seeChat = true;
    private boolean seePlayers = true;

    private Map<String, Object> blocksToWeb = new HashMap<String, Object>();
    private boolean warnMissing = true;

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

        config.addDefault("http.port", httpPort);
        config.addDefault("http.takeover", takeover);
        config.addDefault("http.unbind_method", unbindMethod);

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
        unbreakableBlocks.add("BEDROCK");
        config.addDefault("nc.unbreakable_blocks", unbreakableBlocks);
        config.addDefault("nc.allow_signs", allowSigns);
        config.addDefault("nc.allow_chatting", allowChatting);
        config.addDefault("nc.see_chat", seeChat);
        config.addDefault("nc.see_players", seePlayers);
        blocksToWeb.put("missing", 16); // unknown/unsupported becomes cloud
        blocksToWeb.put("AIR", 0);
        blocksToWeb.put("GRASS", 1);
        blocksToWeb.put("SAND", 2);
        blocksToWeb.put("SMOOTH_BRICK", 3);
        blocksToWeb.put("BRICK", 4);
        blocksToWeb.put("LOG", 5);
        blocksToWeb.put("LOG_2", 5); // wood

        blocksToWeb.put("GOLD_ORE", 70);
        blocksToWeb.put("IRON_ORE", 71);
        blocksToWeb.put("COAL_ORE", 72);
        blocksToWeb.put("LAPIS_ORE", 73);
        blocksToWeb.put("LAPIS_BLOCK", 74);
        // TODO: more ores, for now, showing as stone
        blocksToWeb.put("DIAMOND_ORE", 6);
        blocksToWeb.put("EMERALD_ORE", 6);
        blocksToWeb.put("REDSTONE_ORE", 6);
        blocksToWeb.put("GLOWING_REDSTONE_ORE", 6);
        blocksToWeb.put("QUARTZ_ORE", 6);
        blocksToWeb.put("STONE", 6); // cement, close enough

        blocksToWeb.put("GRAVEL", 7);
        blocksToWeb.put("DIRT", 7);

        blocksToWeb.put("WOOD", 8); // plank

        blocksToWeb.put("SNOW", 9);
        blocksToWeb.put("SNOW_BLOCK", 9);

        blocksToWeb.put("GLASS", 10);
        blocksToWeb.put("COBBLESTONE", 11);
        // TODO",  light stone (12));
        // TODO",  dark stone (13));
        blocksToWeb.put("CHEST", 14);
        blocksToWeb.put("LEAVES", 15);
        blocksToWeb.put("LEAVES_2", 15);
        // TODO",  cloud (16));
        blocksToWeb.put("DOUBLE_PLANT", 17);  // TODO: other double plants, but a lot look like longer long grass
        blocksToWeb.put("LONG_GRASS", 17); // tall grass
        blocksToWeb.put("YELLOW_FLOWER", 18);
        blocksToWeb.put("RED_ROSE", 19);
        blocksToWeb.put("CHORUS_FLOWER", 20);
        // TODO",  sunflower (21));
        // TODO",  white flower (22));
        // TODO",  blue flower (23));

        blocksToWeb.put("WOOL", 61); // note: special case

        blocksToWeb.put("WALL_SIGN", 0); // air, since text is written on block behind it
        blocksToWeb.put("SIGN_POST", 8); // plank TODO",  sign post model

        // Light sources (nonzero toWebLighting()) TODO",  different textures? + allow placement, distinct blocks
        blocksToWeb.put("GLOWSTONE", 64); // #define GLOWING_STONE
        blocksToWeb.put("SEA_LANTERN", 58); // #define COLOR_26 // 58 light blue
        blocksToWeb.put("JACK_O_LANTERN", 53); // #define COLOR_21 // 53 orange
        blocksToWeb.put("REDSTONE_LAMP_ON", 34);
        blocksToWeb.put("REDSTONE_LAMP_OFF", 34); // // #define COLOR_12 // 44 salmon
        blocksToWeb.put("TORCH", 21); // sunflower, looks kinda like a torch
        blocksToWeb.put("REDSTONE_TORCH_OFF", 19);
        blocksToWeb.put("REDSTONE_TORCH_ON", 19); // red flower, vaguely a torch

        // Liquids - currently using color blocks as placeholders since they appear too often
        blocksToWeb.put("STATIONARY_WATER", 56); // aqua color block
        blocksToWeb.put("WATER", 56);
        blocksToWeb.put("STATIONARY_LAVA", 54); // light orange
        blocksToWeb.put("LAVA", 54); // light orange

        // TODO: support more blocks by default
        blocksToWeb.put("BEDROCK", 65);
        blocksToWeb.put("GRAVEL", 66);
        blocksToWeb.put("IRON_BLOCK", 67);
        blocksToWeb.put("GOLD_BLOCK", 68);
        blocksToWeb.put("DIAMOND_BLOCK", 69);
        blocksToWeb.put("SANDSTONE", 75);

        config.addDefault("nc.blocks_to_web", blocksToWeb);
        config.addDefault("nc.warn_missing_blocks_to_web", warnMissing);
        
        httpPort = this.getConfig().getInt("http.port");
        takeover = this.getConfig().getBoolean("http.takeover");
        unbindMethod = this.getConfig().getString("http.unbind_method");

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
        unbreakableBlocks = this.getConfig().getStringList("nc.unbreakable_blocks");
        allowSigns = this.getConfig().getBoolean("nc.allow_signs");
        allowChatting = this.getConfig().getBoolean("nc.allow_chatting");
        seeChat = this.getConfig().getBoolean("nc.see_chat");
        seePlayers = this.getConfig().getBoolean("nc.see_players");
        ConfigurationSection section = this.getConfig().getConfigurationSection("nc.blocks_to_web");
        if (section != null) {
            for (Map.Entry<String, Object> entry : section.getValues(false).entrySet()) {
                blocksToWeb.put(entry.getKey(), entry.getValue());
            }
        }
        warnMissing = this.getConfig().getBoolean("nc.warn_missing_blocks_to_web");

        saveConfig();

        checkUnbind(takeover, unbindMethod);

        final Plugin plugin = this;

        // Run in a delayed task to ensure all worlds are loaded on startup (not only load: POSTWORLD).
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
            @Override
            public void run() {

                webSocketServerThread = new WebSocketServerThread(plugin, httpPort, debug);

                webSocketServerThread.blockBridge = new BlockBridge(webSocketServerThread, world, x_center, y_center,
                        z_center, radius, y_offset, allowBreakPlaceBlocks, allowSigns, blocksToWeb, warnMissing,
                        unbreakableBlocks);
                webSocketServerThread.playersBridge = new PlayersBridge(webSocketServerThread, allowChatting, seeChat, seePlayers);
                webSocketServerThread.webPlayerBridge = new WebPlayerBridge(webSocketServerThread, setCustomNames,
                        disableGravity, disableAI, entityClassName, entityMoveSandbox, entityDieDisconnect);

                // Register our events
                PluginManager pm = getServer().getPluginManager();

                pm.registerEvents(new BlockListener(webSocketServerThread.blockBridge), plugin);
                pm.registerEvents(new PlayersListener(webSocketServerThread.playersBridge), plugin);
                pm.registerEvents(new EntityListener(webSocketServerThread.webPlayerBridge), plugin);

                // Register our commands
                getCommand("websandbox").setExecutor(new WsCommand(webSocketServerThread));

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
        httpPort = server.getPort();

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
