package io.github.satoshinm.WebSandboxMC.bridge;

import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.*;
import org.bukkit.block.Block;

/**
 * Bridges blocks in the world, translates between coordinate systems
 */
public class BlockBridge {

    public WebSocketServerThread webSocketServerThread;
    private final int x_center, y_center, z_center, radius, y_offset;
    public final World world;
    public Location spawnLocation;

    public BlockBridge(WebSocketServerThread webSocketServerThread, int x_center, int y_center, int z_center, int radius, int y_offset) {
        this.webSocketServerThread = webSocketServerThread;

        this.x_center = x_center;
        this.y_center = y_center;
        this.z_center = z_center;

        this.radius = radius;

        this.y_offset = y_offset;

        // TODO: configurable world
        this.world = Bukkit.getWorlds().get(0);

        // TODO: configurable spawn within range of sandbox, right now, it is the center of the sandbox
        this.spawnLocation = new Location(this.world, this.x_center, this.y_center, this.z_center);
    }

    // Send the client the initial section of the world when they join
    @SuppressWarnings("deprecation") // Block#getData()
    public void sendWorld(final Channel channel) {
        for (int i = -radius; i < radius; ++i) {
            for (int j = -radius; j < radius; ++j) {
                for (int k = -radius; k < radius; ++k) {
                    Block block = world.getBlockAt(i + x_center, j + y_center, k + z_center);
                    //int type = toWebBlockType(block.getType(), block.getData());

                    //webSocketServerThread.sendLine(channel, "B,0,0," + (i + radius) + "," + (j + radius + y_offset) + "," + (k + radius) + "," + type);
                    notifyBlockUpdate(block.getLocation(), block.getType(), block.getData());
                }
            }
        }
        webSocketServerThread.sendLine(channel,"K,0,0,1");
        webSocketServerThread.sendLine(channel, "R,0,0");

        // Move player on top of the new blocks
        int x_start = radius;
        int y_start = world.getHighestBlockYAt(x_center, y_center) + 1 - radius - y_offset;
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
        Material material = toBukkitBlockType(type);
        int blockdata = toBukkitBlockData(type);
        Location location = toBukkitLocation(x, y, z);

        if (!withinSandboxRange(location)) {
            System.out.println("client tried to modify outside of sandbox! "+location);
            webSocketServerThread.sendLine(ctx.channel(), "T,You cannot build at ("+x+","+y+","+z+")");
            // TODO: Clear the block, fix this (set to air)
            /*
            webSocketServerThread.sendLine(ctx.channel(), "B,0,0,"+ox+","+oy+","+oz+",0");
            webSocketServerThread.sendLine(ctx.channel(), "R,0,0");
            */
            return;
        }

        Block block = world.getBlockAt(location);
        if (block == null) {
            System.out.println("no such block at " + location); // does this happen?
            return;
        }

        System.out.println("setting block at "+location+" to "+material);
        if (blockdata != -1) {
            block.setTypeIdAndData(material.getId(), (byte) blockdata, true);
        } else {
            block.setType(material);
        }

        // Notify other web clients - note they will have the benefit of seeing the untranslated block (feature or bug?)
        webSocketServerThread.broadcastLineExcept(ctx.channel().id(), "B,0,0," + x + "," + y + "," + z + "," + type);
        webSocketServerThread.broadcastLineExcept(ctx.channel().id(), "R,0,0");
    }

    // Handle the bukkit world changing a block, tell all web clients
    public void notifyBlockUpdate(Location location, Material material, byte data) {
        //System.out.println("bukkit block ("+x+","+y+","+z+") was set to "+material);

        if (!withinSandboxRange(location)) {
            // Clients don't need to know about every block change on the server, only within the sandbox
            return;
        }


        // Send to all web clients to let them know it changed using the "B," command
        int type = toWebBlockType(material, data);

        int x = toWebLocationBlockX(location);
        int y = toWebLocationBlockY(location);
        int z = toWebLocationBlockZ(location);

        webSocketServerThread.broadcastLine("B,0,0,"+x+","+y+","+z+","+type);
        webSocketServerThread.broadcastLine("R,0,0");

        int light_level = toWebLighting(material, data);
        if (light_level != 0) {
            webSocketServerThread.broadcastLine("L,0,0,"+x+","+y+","+z+"," + light_level);
        }

        //System.out.println("notified block update: ("+x+","+y+","+z+") to "+type);
    }

    private int toWebLighting(Material material, byte data) {
        switch (material) {
            case GLOWSTONE:
            case SEA_LANTERN:
            case JACK_O_LANTERN:
            case BEACON:
            case REDSTONE_LAMP_ON: // TODO: get notified when toggles on/off
                return 15;

            case TORCH:
                return 14;

            case REDSTONE_TORCH_ON:
                return 7;
        }

        return 0;
    }

    // Translate web<->bukkit blocks
    // TODO: refactor to remove all bukkit dependency in this class (enums strings?), generalize to can support others
    private int toWebBlockType(Material material, byte data) {
        int type;
        switch (material) {
            case AIR:
                type = 0;
                break;
            case GRASS:
                type = 1;
                break;
            case SAND:
                type = 2;
                break;
            case SMOOTH_BRICK:
                type = 3;
                break;
            case BRICK:
                type = 4;
                break;
            case LOG:
            case LOG_2:
                type = 5; // wood
                break;
            // TODO: ores, for now, showing as stone
            case COAL_ORE:
            case IRON_ORE:
                //case DIAMOND_ORE:
                //case EMERALD_ORE:
            case REDSTONE_ORE:
            case GLOWING_REDSTONE_ORE:
            case LAPIS_ORE:
                //case QUARTZ_ORE:
            case GOLD_ORE:
            case STONE:
                type = 6; // cement, close enough
                break;
            case GRAVEL:
            case DIRT:
                type = 7;
                break;
            case WOOD:
                type = 8; // plank
                break;
            case SNOW:
            case SNOW_BLOCK:;
                type = 9;
                break;
            case GLASS:
                type = 10;
                break;
            case COBBLESTONE:
                type = 11;
                break;
            // TODO: light stone (12)
            // TODO: dark stone (13)
            case CHEST:
                type = 14;
                break;
            case LEAVES:
            case LEAVES_2:
                type = 15;
                break;
            // TODO: cloud (16)
            case DOUBLE_PLANT: // TODO: other double plants, but a lot look like longer long grass
            case LONG_GRASS:
                type = 17; // tall grass
                break;
            case YELLOW_FLOWER:
                type = 18;
                break;
            case RED_ROSE:
                type = 19;
                break;
            case CHORUS_FLOWER:
                type = 20;
                break;
            // TODO: sunflower (21)
            // TODO: white flower (22)
            // TODO: blue flower (23)

            case WOOL:
            {
                switch (data) {
                    case 0: // white
                        type = 61; // #define COLOR_29 // 61 white
                        break;
                    case 1: // orange
                        type = 53;
                        break;
                    case 2: // magenta
                        type = 43; // #define COLOR_11 // 43 crimson
                        break;
                    case 3: // light blue
                        type = 58; // #define COLOR_26 // 58 light blue
                        break;
                    case 4: // yellow
                        type = 32; // #define COLOR_00 // 32 yellow
                        break;
                    case 5: // lime
                        type = 46; // #define COLOR_14 // 46 puke green
                        break;
                    case 6: // pink
                        type = 45; // #define COLOR_13 // 45 pink
                        break;
                    case 7: // gray
                        type = 41; // #define COLOR_09 // 41 darker gray
                        break;
                    case 8: // light gray
                        type = 50; // #define COLOR_18 // 50 medium gray
                        break;
                    case 9: // cyan
                        type = 59; // #define COLOR_27 // 59 foam green
                        break;
                    case 10: // purple
                        type = 39; // #define COLOR_07 // 39 purple
                        break;
                    case 11: // blue
                        type = 57; // #define COLOR_25 // 57 blue
                        break;
                    case 12: // brown
                        type = 47; // #define COLOR_15 // 47 poop brown
                        break;
                    case 13: // green
                        type = 34; // #define COLOR_02 // 34 green
                        break;
                    case 14: // red
                        type = 44; // #define COLOR_12 // 44 salmon
                        break;
                    default:
                    case 15: // black
                        type = 48; // #define COLOR_16 // 48 black
                        break;
                }
                break;
            }

            // Light sources (nonzero toWebLighting()) TODO: different textures? + allow placement, distinct blocks
            case GLOWSTONE:
                type = 32; // #define COLOR_00 // 32 yellow
                break;
            case SEA_LANTERN:
                type = 58; // #define COLOR_26 // 58 light blue
                break;
            case JACK_O_LANTERN:
                type = 53; // #define COLOR_21 // 53 orange
                break;
            case REDSTONE_LAMP_ON:
            case REDSTONE_LAMP_OFF:
                type = 34; // // #define COLOR_12 // 44 salmon
                break;
            case TORCH:
                type = 21; // sunflower, looks kinda like a torch
                break;
            case REDSTONE_TORCH_OFF:
            case REDSTONE_TORCH_ON:
                type = 19; // red flower, vaguely a torch
                break;

            default:
                System.out.println("unknown block type="+material);
                // unknown/unsupported becomes cloud
                // TODO: support more
                type = 16;

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
            //case 22: material = Material.white flower
            //case 23: material = Material.blue flower
            default:
                System.out.println("untranslated web block id "+type);
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
}
