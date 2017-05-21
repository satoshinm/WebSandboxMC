package io.github.satoshinm.WebSandboxMC.bridge;

import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Bridges blocks in the world, translates between coordinate systems
 */
public class BlockBridge {

    public WebSocketServerThread webSocketServerThread;
    private final int x_center, y_center, z_center, radius, y_offset;
    public final World world;
    public Location spawnLocation;
    private boolean allowBreakPlaceBlocks;
    private boolean allowSigns;
    private Map<Material, Integer> blocksToWeb;
    private int blocksToWebMissing = 16; // unknown/unsupported becomes cloud, if key missing
    private boolean warnMissing;
    private List<Material> unbreakableBlocks;
    private String textureURL;

    public BlockBridge(WebSocketServerThread webSocketServerThread,
                       String world, int x_center, int y_center, int z_center, int radius, int y_offset,
                       boolean allowBreakPlaceBlocks, boolean allowSigns, Map<String, Object> blocksToWebOverride,
                       boolean warnMissing, List<String> unbreakableBlocks, String textureURL) {
        this.webSocketServerThread = webSocketServerThread;

        this.x_center = x_center;
        this.y_center = y_center;
        this.z_center = z_center;

        this.radius = radius;

        this.y_offset = y_offset;

        if (world == null || "".equals(world)) {
            this.world = Bukkit.getWorlds().get(0);
        } else {
            this.world = Bukkit.getWorld(world);
        }
        if (this.world == null) {
            throw new IllegalArgumentException("World not found: " + world);
        }

        // TODO: configurable spawn within range of sandbox, right now, it is the center of the sandbox
        this.spawnLocation = new Location(this.world, this.x_center, this.y_center, this.z_center);

        this.allowBreakPlaceBlocks = allowBreakPlaceBlocks;
        this.allowSigns = allowSigns;

        this.blocksToWeb = new HashMap<Material, Integer>();
        Map<String, Integer> blocksToWebDefault = new HashMap<String, Integer>();

        blocksToWebDefault.put("missing", 16); // unknown/unsupported becomes cloud
        blocksToWebDefault.put("AIR", 0);
        blocksToWebDefault.put("GRASS", 1);
        blocksToWebDefault.put("SAND", 2);
        blocksToWebDefault.put("SMOOTH_BRICK", 3);
        blocksToWebDefault.put("BRICK", 4);
        blocksToWebDefault.put("LOG", 5);
        blocksToWebDefault.put("LOG_2", 5); // wood

        blocksToWebDefault.put("GOLD_ORE", 70);
        blocksToWebDefault.put("IRON_ORE", 71);
        blocksToWebDefault.put("COAL_ORE", 72);
        blocksToWebDefault.put("LAPIS_ORE", 73);
        blocksToWebDefault.put("LAPIS_BLOCK", 74);
        blocksToWebDefault.put("DIAMOND_ORE", 48);
        blocksToWebDefault.put("REDSTONE_ORE", 49);
        blocksToWebDefault.put("REDSTONE_ORE", 49);
        // TODO: more ores, for now, showing as stone
        blocksToWebDefault.put("EMERALD_ORE", 6);
        blocksToWebDefault.put("QUARTZ_ORE", 6);
        blocksToWebDefault.put("STONE", 6); // cement, close enough

        blocksToWebDefault.put("GRAVEL", 7);
        blocksToWebDefault.put("DIRT", 7);

        blocksToWebDefault.put("WOOD", 8); // plank

        blocksToWebDefault.put("SNOW", 9);
        blocksToWebDefault.put("SNOW_BLOCK", 9);

        blocksToWebDefault.put("GLASS", 10);
        blocksToWebDefault.put("COBBLESTONE", 11);
        // TODO",  light stone (12));
        // TODO",  dark stone (13));
        blocksToWebDefault.put("CHEST", 14);
        blocksToWebDefault.put("LEAVES", 15);
        blocksToWebDefault.put("LEAVES_2", 15);
        // TODO",  cloud (16));
        blocksToWebDefault.put("DOUBLE_PLANT", 17);  // TODO: other double plants, but a lot look like longer long grass
        blocksToWebDefault.put("LONG_GRASS", 17); // tall grass
        blocksToWebDefault.put("YELLOW_FLOWER", 18);
        blocksToWebDefault.put("RED_ROSE", 19);
        blocksToWebDefault.put("CHORUS_FLOWER", 20);
        // TODO",  sunflower (21));
        // TODO",  white flower (22));
        // TODO",  blue flower (23));

        blocksToWebDefault.put("WOOL", 32); // note: special case

        blocksToWebDefault.put("WALL_SIGN", 0); // air, since text is written on block behind it
        blocksToWebDefault.put("SIGN_POST", 8); // plank TODO",  sign post model

        // Light sources (nonzero toWebLighting()) TODO",  different textures? + allow placement, distinct blocks
        blocksToWebDefault.put("GLOWSTONE", 64); // #define GLOWING_STONE
        blocksToWebDefault.put("SEA_LANTERN", 35); // light blue wool
        blocksToWebDefault.put("JACK_O_LANTERN", 33); // orange wool
        blocksToWebDefault.put("REDSTONE_LAMP_ON", 46); // red wool
        blocksToWebDefault.put("REDSTONE_LAMP_OFF", 46); // red wool
        blocksToWebDefault.put("TORCH", 21); // sunflower, looks kinda like a torch
        blocksToWebDefault.put("REDSTONE_TORCH_OFF", 19);
        blocksToWebDefault.put("REDSTONE_TORCH_ON", 19); // red flower, vaguely a torch

        // Liquids - currently using color blocks as placeholders since they appear too often
        blocksToWebDefault.put("STATIONARY_WATER", 35); // light blue wool
        blocksToWebDefault.put("WATER", 35); // light blue wool
        blocksToWebDefault.put("STATIONARY_LAVA", 35); // orange wool
        blocksToWebDefault.put("LAVA", 35); // orange wool

        // TODO: support more blocks by default
        blocksToWebDefault.put("BEDROCK", 65);
        blocksToWebDefault.put("GRAVEL", 66);
        blocksToWebDefault.put("IRON_BLOCK", 67);
        blocksToWebDefault.put("GOLD_BLOCK", 68);
        blocksToWebDefault.put("DIAMOND_BLOCK", 69);
        blocksToWebDefault.put("SANDSTONE", 75);
        blocksToWebDefault.put("BOOKSHELF", 50);
        blocksToWebDefault.put("MOSSY_COBBLESTONE", 51);
        blocksToWebDefault.put("OBSIDIAN", 52);
        blocksToWebDefault.put("WORKBENCH", 53);
        blocksToWebDefault.put("FURNACE", 54);
        blocksToWebDefault.put("BURNING_FURNACE", 55);
        blocksToWebDefault.put("MOB_SPAWNER", 56);
        blocksToWebDefault.put("SNOW_BLOCK", 57);
        blocksToWebDefault.put("ICE", 58);
        blocksToWebDefault.put("CLAY", 59);
        blocksToWebDefault.put("JUKEBOX", 60);
        blocksToWebDefault.put("CACTUS", 61);
        blocksToWebDefault.put("MYCEL", 62);
        blocksToWebDefault.put("NETHERRACK", 63);

        // First setup the defaults from above - don't loudly log failures here since they are either my fault, an error
        // during development, or unsupported materials from an older/newer version of Bukkit
        for (String materialString : blocksToWebDefault.keySet()) {
            int n = ((Integer) blocksToWebDefault.get(materialString)).intValue();

            Material material = Material.getMaterial(materialString);
            if (materialString.equals("missing")) {
                this.blocksToWebMissing = n;
            } else {
                if (material == null) {
                    // maybe server doesn't have this material
                    webSocketServerThread.log(Level.FINEST, "(internal) blocks_to_web invalid material ignored: " + materialString);
                    continue;
                }

                this.blocksToWeb.put(material, n);
            }
        }

        // Then override from config, if any
        for (String materialString : blocksToWebOverride.keySet()) {
            Object object = blocksToWebOverride.get(materialString);

            int n = 0;
            if (object instanceof String) {
                n = Integer.parseInt((String) object);
            } else if (object instanceof Integer) {
                n = (Integer) object;
            } else {
                webSocketServerThread.log(Level.WARNING, "blocks_to_web_override invalid integer ignored: "+n+", in "+object);
                continue;
            }


            Material material = Material.getMaterial(materialString);
            if (materialString.equals("missing")) {
                this.blocksToWebMissing = n;
                this.webSocketServerThread.log(Level.FINEST, "blocks_to_web_override missing value to set to: "+n);
            } else {
                if (material == null) {
                    webSocketServerThread.log(Level.WARNING, "blocks_to_web_override invalid material ignored: " + materialString);
                    continue;
                }

                this.blocksToWeb.put(material, n);
                this.webSocketServerThread.log(Level.FINEST, "blocks_to_web_override: " + material + " = " + n);
            }
        }

        this.warnMissing = warnMissing;

        this.unbreakableBlocks = new ArrayList<Material>();
        for (String materialString : unbreakableBlocks) {
            Material material = Material.getMaterial(materialString);
            if (material == null) {
                webSocketServerThread.log(Level.WARNING, "unbreakable_blocks invalid material ignored: " + materialString);
                continue;
            }
            this.unbreakableBlocks.add(material);
        }

        this.textureURL = textureURL;
    }

    // Send the client the initial section of the world when they join
    @SuppressWarnings("deprecation") // Block#getData()
    public void sendWorld(final Channel channel) {
        if (textureURL != null) {
            webSocketServerThread.sendLine(channel, "t," + textureURL);
        }

        boolean thereIsAWorld = false;
        // TODO: bulk block update compressed, for efficiency (this is very efficient, but surprisingly works!)
        for (int i = -radius; i < radius; ++i) {
            for (int j = -radius; j < radius; ++j) {
                for (int k = -radius; k < radius; ++k) {
                    Block block = world.getBlockAt(i + x_center, j + y_center, k + z_center);
                    //int type = toWebBlockType(block.getType(), block.getData());

                    //webSocketServerThread.sendLine(channel, "B,0,0," + (i + radius) + "," + (j + radius + y_offset) + "," + (k + radius) + "," + type);
                    thereIsAWorld |= setBlockUpdate(block.getLocation(), block.getType(), block.getData());
                }
            }
        }
        webSocketServerThread.sendLine(channel,"K,0,0,1");
        webSocketServerThread.sendLine(channel, "R,0,0");

        if (!thereIsAWorld) {
            webSocketServerThread.sendLine(channel, "T,No blocks sent (server misconfiguration, check x/y/z_center)");
            webSocketServerThread.log(Level.WARNING, "No valid blocks were found centered around ("+
                x_center + "," + y_center + "," + z_center + ") radius " + radius +
                    ", try changing these values or blocks_to_web in the configuration. All blocks were air or missing!");
        }

        // Move player on top of the new blocks
        int x_start = radius;
        int y_start = world.getHighestBlockYAt(x_center, z_center) - radius - y_offset;
        int z_start = radius;
        int rotation_x = 0;
        int rotation_y = 0;
        webSocketServerThread.sendLine(channel, "U,1," + x_start + "," + y_start + "," + z_start + "," + rotation_x + "," + rotation_y );
    }

    public boolean withinSandboxRange(Location location) {
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        if (x >= x_center + radius || x < x_center - radius) {
            return false;
        }
        if (y >= y_center + radius || y < y_center - radius) {
            return false;
        }
        if (z >= z_center + radius || z < z_center - radius) {
            return false;
        }
        return true;
    }

    public Location toBukkitLocation(int x, int y, int z) {
        x += -radius + x_center;
        y += -radius + y_center - y_offset;
        z += -radius + z_center;

        Location location = new Location(world, x, y, z);

        return location;
    }

    public Location toBukkitPlayerLocation(double x, double y, double z) {
        x += -radius + x_center;
        y += -radius + y_center - y_offset;
        z += -radius + z_center;

        Location location = new Location(world, x, y, z);

        return location;
    }

    public int toWebLocationBlockX(Location location) { return location.getBlockX() - (-radius + x_center); }

    public int toWebLocationBlockY(Location location) { return location.getBlockY() - (-radius + y_center - y_offset); }

    public int toWebLocationBlockZ(Location location) { return location.getBlockZ() - (-radius + z_center); }

    public double toWebLocationEntityX(Location location) { return location.getX() - (-radius + x_center); }

    public double toWebLocationEntityY(Location location) { return location.getY() - (-radius + y_center - y_offset); }

    public double toWebLocationEntityZ(Location location) { return location.getZ() - (-radius + z_center); }

    // Handle the web client changing a block, update the bukkit world
    @SuppressWarnings("deprecation") // for Block#setTypeIdAndData
    public void clientBlockUpdate(ChannelHandlerContext ctx, int x, int y, int z, int type) {
        if (!allowBreakPlaceBlocks) {
            webSocketServerThread.sendLine(ctx.channel(), "T,Breaking/placing blocks not allowed");
            // TODO: set back to original block to revert on client
            return;
        }

        Material material = toBukkitBlockType(type);
        int blockdata = toBukkitBlockData(type);
        Location location = toBukkitLocation(x, y, z);

        if (!withinSandboxRange(location)) {
            webSocketServerThread.log(Level.FINEST, "client tried to modify outside of sandbox! "+location); // not severe, since not prevented client-side
            webSocketServerThread.sendLine(ctx.channel(), "T,You cannot build at ("+x+","+y+","+z+")");
            // TODO: Clear the block, fix this (set to air)
            /*
            webSocketServerThread.sendLine(ctx.channel(), "B,0,0,"+ox+","+oy+","+oz+",0");
            webSocketServerThread.sendLine(ctx.channel(), "R,0,0");
            */
            return;
        }

        Block previousBlock = location.getBlock();
        Material previousMaterial = previousBlock.getType();
        if (unbreakableBlocks.contains(previousMaterial) || unbreakableBlocks.contains(material)) {
            webSocketServerThread.log(Level.FINEST, "client tried to change or place unbreakable block at " +
                    location + " of type previousMaterial="+previousMaterial+" to material="+material);
            if (unbreakableBlocks.contains(previousMaterial)) {
                webSocketServerThread.sendLine(ctx.channel(), "T,You cannot break blocks of type " + previousMaterial);
            } else if (unbreakableBlocks.contains(material)) {
                webSocketServerThread.sendLine(ctx.channel(), "T,You cannot place blocks of type " + material);
            }
            // Revert on client
            int previousType = toWebBlockType(previousMaterial, (byte) 0);
            webSocketServerThread.sendLine(ctx.channel(), "B,0,0,"+x+","+y+","+z+","+previousType);
            webSocketServerThread.sendLine(ctx.channel(), "R,0,0");
            return;
        }

        Block block = world.getBlockAt(location);
        if (block == null) {
            webSocketServerThread.log(Level.WARNING, "web client no such block at " + location); // does this happen?
            return;
        }

        webSocketServerThread.log(Level.FINEST, "setting block at "+location+" to "+material);
        if (blockdata != -1) {
            block.setTypeIdAndData(material.getId(), (byte) blockdata, true);
        } else {
            block.setType(material);
        }

        // Notify other web clients - note they will have the benefit of seeing the untranslated block (feature or bug?)
        webSocketServerThread.broadcastLineExcept(ctx.channel().id(), "B,0,0," + x + "," + y + "," + z + "," + type);
        webSocketServerThread.broadcastLineExcept(ctx.channel().id(), "R,0,0");
    }


    // Handle the bukkit world changing a block, tell all web clients and refresh
    public void notifyBlockUpdate(Location location, Material material, byte data) {
        webSocketServerThread.log(Level.FINEST, "bukkit block at "+location+" was set to "+material);

        if (!withinSandboxRange(location)) {
            // Clients don't need to know about every block change on the server, only within the sandbox
            return;
        }

        setBlockUpdate(location, material, data);

        webSocketServerThread.broadcastLine("R,0,0");
    }

    @SuppressWarnings("deprecation") // for Block#getData
    private boolean setBlockUpdate(Location location, Material material, byte data) {
        // Send to all web clients to let them know it changed using the "B," command
        int type = toWebBlockType(material, data);
        boolean substantial;

        if (type == -1) {
            if (warnMissing) {
                webSocketServerThread.log(Level.WARNING, "Block type missing from blocks_to_web: " + material + " at " + location);
            }
            type = blocksToWebMissing;
            substantial = false;
        } else if (type == 0) {
            substantial = false;
        } else {
            substantial = true;
        }

        int x = toWebLocationBlockX(location);
        int y = toWebLocationBlockY(location);
        int z = toWebLocationBlockZ(location);

        webSocketServerThread.broadcastLine("B,0,0,"+x+","+y+","+z+","+type);

        int light_level = toWebLighting(material, data);
        if (light_level != 0) {
            webSocketServerThread.broadcastLine("L,0,0,"+x+","+y+","+z+"," + light_level);
        }

        if (material == Material.WALL_SIGN || material == material.SIGN_POST) {
            Block block = location.getWorld().getBlockAt(location);
            BlockState blockState = block.getState();
            if (blockState instanceof Sign) {
                Sign sign = (Sign) blockState;

                notifySignChange(block.getLocation(), block.getType(), block.getData(), sign.getLines());
            }
        }

        webSocketServerThread.log(Level.FINEST, "notified block update: ("+x+","+y+","+z+") to "+type);

        return substantial; // was something "real" set? (not air, not missing)
    }

    private int toWebLighting(Material material, byte data) {
        // See http://minecraft.gamepedia.com/Light#Blocks
        // Note not all of these may be fully supported yet
        switch (material) {
            case BEACON:
            case ENDER_PORTAL:
            case FIRE:
            case GLOWSTONE:
            case JACK_O_LANTERN:
            case LAVA:
            case REDSTONE_LAMP_ON: // TODO: get notified when toggles on/off
            case SEA_LANTERN:
            case END_ROD:
                return 15;

            case TORCH:
                return 14;

            case BURNING_FURNACE:
                return 13;

            case PORTAL:
                return 11;

            case GLOWING_REDSTONE_ORE:
                return 9;

            case ENDER_CHEST:
            case REDSTONE_TORCH_ON:
                return 7;

            case MAGMA:
                return 3;

            case BREWING_STAND:
            case BROWN_MUSHROOM:
            case DRAGON_EGG:
            case ENDER_PORTAL_FRAME:
                return 1;
        }

        return 0;
    }

    // Translate web<->bukkit blocks
    // TODO: refactor to remove all bukkit dependency in this class (enums strings?), generalize to can support others
    private int toWebBlockType(Material material, byte data) {
        if (!blocksToWeb.containsKey(material)) {
            return -1;
        }

        int type = blocksToWeb.get(material);

        if (type == 32) { // special case for wool / color block, not yet configurable
            switch (data) {
                case 0: // white
                    type = 32;
                    break;
                case 1: // orange
                    type = 33;
                    break;
                case 2: // magenta
                    type = 34;
                    break;
                case 3: // light blue
                    type = 35;
                    break;
                case 4: // yellow
                    type = 36;
                    break;
                case 5: // lime
                    type = 37;
                    break;
                case 6: // pink
                    type = 38;
                    break;
                case 7: // gray
                    type = 39;
                    break;
                case 8: // light gray
                    type = 40;
                    break;
                case 9: // cyan
                    type = 41;
                    break;
                case 10: // purple
                    type = 42;
                    break;
                case 11: // blue
                    type = 43;
                    break;
                case 12: // brown
                    type = 44;
                    break;
                case 13: // green
                    type = 45;
                    break;
                case 14: // red
                    type = 46;
                    break;
                default:
                case 15: // black
                    type = 47;
                    break;
            }
        }

        return type;
    }

    private Material toBukkitBlockType(int type) {
        Material material;
        if (type >= 32 && type <= 63) return Material.WOOL;
        // TODO: refactor reverse translation
        switch (type) {
            case 0: material = Material.AIR; break;
            case 1: material = Material.GRASS; break;
            case 2: material = Material.SAND; break;
            case 3: material = Material.SMOOTH_BRICK; break; // "smooth stone brick"
            case 4: material = Material.BRICK; break;
            case 5: material = Material.LOG; break;
            case 6: material = Material.STONE; break; // "cement"
            case 7: material = Material.DIRT; break;
            case 8: material = Material.WOOD; break;
            case 9: material = Material.SNOW_BLOCK; break;
            case 10: material = Material.GLASS; break;
            case 11: material = Material.COBBLESTONE; break;
            //case 12: material = Material. light stone?
            //case 13: material = Material. dark stone?
            case 14: material = Material.CHEST; break;
            case 15: material = Material.LEAVES; break;
            //case 16: material = Material.clouds; break; // clouds
            case 17: material = Material.LONG_GRASS; break;
            case 18: material = Material.YELLOW_FLOWER; break;
            case 19: material = Material.RED_ROSE; break;
            case 20: material = Material.CHORUS_FLOWER; break;
            case 21: material = Material.DOUBLE_PLANT; break; // sunflower
            case 22: material = Material.RED_ROSE; break; // TODO: white flower
            case 23: material = Material.YELLOW_FLOWER; break; // TODO: blue flower
            case 64: material = Material.GLOWSTONE; break;
            default:
                webSocketServerThread.log(Level.WARNING, "untranslated web block id "+type);
                material = Material.DIAMOND_ORE; // placeholder TODO fix
        }
        return material;
    }

    @SuppressWarnings("deprecation") // DyeColor#getData()
    private int toBukkitBlockData(int type) {
        DyeColor color = null;
        switch (type) {
            // Craft has 32 color block types, but MC only 16 - not 1:1, but try to get close enough
            case 32: // #define COLOR_00 // 32 yellow
                color = DyeColor.YELLOW; break;
            case 33: // #define COLOR_01 // 33 light green
            case 34: // #define COLOR_02 // 34 green
            case 35: // #define COLOR_03 // 35 sea green
                color = DyeColor.GREEN; break;
            case 36: // #define COLOR_04 // 36 light brown
            case 37: // #define COLOR_05 // 37 medium brown
            case 38: // #define COLOR_06 // 38 dark brown
                color = DyeColor.BROWN; break;
            case 39: // #define COLOR_07 // 39 purple
                color = DyeColor.PURPLE; break;
            case 40: // #define COLOR_08 // 40 dark gray
            case 41: // #define COLOR_09 // 41 darker gray
                color = DyeColor.GRAY; break;
            case 42: // #define COLOR_10 // 42 light purple
                color = DyeColor.PURPLE; break;
            case 43: // #define COLOR_11 // 43 crimson
                color = DyeColor.MAGENTA; break;
            case 44: // #define COLOR_12 // 44 salmon
                color = DyeColor.RED; break;
            case 45: // #define COLOR_13 // 45 pink
                color = DyeColor.PINK; break;
            case 46: // #define COLOR_14 // 46 puke green
                color = DyeColor.LIME; break;
            case 47: // #define COLOR_15 // 47 poop brown
                color = DyeColor.BROWN; break;
            case 48: // #define COLOR_16 // 48 black
                color = DyeColor.BLACK; break;
            case 49: // #define COLOR_17 // 49 dark gray
                color = DyeColor.GRAY; break;
            case 50: // #define COLOR_18 // 50 medium gray
                color = DyeColor.SILVER; break;
            case 51: // #define COLOR_19 // 51 leather
            case 52: // #define COLOR_20 // 52 tan
            case 53: // #define COLOR_21 // 53 orange
            case 54: // #define COLOR_22 // 54 light orange
            case 55: // #define COLOR_23 // 55 sand
                color = DyeColor.ORANGE; break;
            case 56: // #define COLOR_24 // 56 aqua
            case 57: // #define COLOR_25 // 57 blue
                color = DyeColor.BLUE; break;
            case 58: // #define COLOR_26 // 58 light blue
                color = DyeColor.LIGHT_BLUE; break;
            case 59: // #define COLOR_27 // 59 foam green
                color = DyeColor.CYAN; break;
            case 60: // #define COLOR_28 // 60 cloud
            case 61: // #define COLOR_29 // 61 white
            case 62: // #define COLOR_30 // 62 offwhite
                color = DyeColor.WHITE; break;
            case 63: // #define COLOR_31 // 63 gray
                color = DyeColor.GRAY; break;
        }
        if (color != null) {
            return color.getWoolData();
        }
        return -1;
    }

    public void notifySignChange(Location location, Material material, byte data, String[] lines) {
        int x = toWebLocationBlockX(location);
        int y = toWebLocationBlockY(location);
        int z = toWebLocationBlockZ(location);


        // data is packed bitfield, see http://minecraft.gamepedia.com/Sign#Block_data
        // Craft's faces:
        // 0 - west
        // 1 - east
        // 2 - north
        // 3 - south
        // 4 - top, rotated 1
        // 5 - top, rotated 2
        // 6 - top, rotated 3
        // 7 - top, rotated 4
        int face = 7;
        if (material == Material.WALL_SIGN) {
            // wallsigns, attached to block behind
            switch (data) {
                case 2: // north
                    face = 2; // north
                    z += 1;
                    break;
                case 3: // south
                    face = 3; // south
                    z -= 1;
                    break;
                case 4: // west
                    face = 0; // west
                    x += 1;
                    break;
                case 5: // east
                    face = 1; // east
                    x -= 1;
                    break;
            }
        } else if (material == Material.SIGN_POST) {
            // standing sign, on the block itself
            // TODO: support more fine-grained directions, right now Craft only four cardinal
            switch (data) {
                case 0: // south
                case 1: // south-southwest
                case 2: // southwest
                    face = 3; // south
                    break;

                case 3: // west-southwest
                case 4: // west
                case 5: // west-northwest
                case 6: // northwest
                    face = 0; // west
                    break;

                case 7: // north-northwest
                case 8: // north
                case 9: // north-northeast
                case 10: // northeast
                    face = 2; // north
                    break;

                case 11: // east-northeast
                case 12: // east
                case 13: // east-southeast
                case 14: // southeast
                case 15: // south-southeast
                    face = 1; // east
                    break;
            }
        }

        webSocketServerThread.log(Level.FINEST, "sign change: "+location+", data="+data);
        String text = "";
        for (int i = 0; i < lines.length; ++i) {
            text += lines[i] + " "; // TODO: support explicit newlines; Craft wraps sign text lines automatically
        }
        if (text.contains("\n")) {
            // \n is used as a command terminator in the Craft protocol (but ',' is acceptable)
            text = text.replaceAll("\n", " ");
        }

        webSocketServerThread.broadcastLine("S,0,0,"+x+","+y+","+z+","+face+","+text);
        webSocketServerThread.broadcastLine("R,0,0");
    }

    @SuppressWarnings("deprecation") // Block#setData()
    public void clientNewSign(ChannelHandlerContext ctx, int x, int y, int z, int face, String text) {
        if (!allowSigns) {
            webSocketServerThread.sendLine(ctx.channel(), "T,Writing on signs is not allowed");
            // TODO: revert on client
            return;
        }

        byte data = 0;
        switch (face) {
            case 0: // west
                data = 4; // west
                x -= 1;
                break;
            case 1: // east
                data = 5; // east
                x += 1;
                break;
            case 2: // north
                data = 2; // north
                z -= 1;
                break;
            case 3: // south
                data = 3; // south
                z += 1;
                break;
        }

        Location location = toBukkitLocation(x, y, z);
        if (!withinSandboxRange(location)) {
            webSocketServerThread.log(Level.FINEST, "client tried to write a sign outside sandbox range");
            return;
        }

        Block block = location.getWorld().getBlockAt(location);
        block.setType(Material.WALL_SIGN);
        block.setData(data);
        webSocketServerThread.log(Level.FINEST, "setting sign at "+location+" data="+data);
        BlockState blockState = block.getState();
        if (!(blockState instanceof Sign)) {
            webSocketServerThread.log(Level.WARNING, "failed to place sign at "+location);
            return;
        }
        Sign sign = (Sign) blockState;

        // TODO: text lines by 15 characters into 5 lines
        sign.setLine(0, text);
        sign.update();

        // SignChangeEvent not posted when signs created programmatically; notify web clients ourselves
        notifySignChange(location, block.getType(), block.getData(), sign.getLines());
    }
}
