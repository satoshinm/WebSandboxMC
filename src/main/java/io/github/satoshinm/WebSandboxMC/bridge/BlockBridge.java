package io.github.satoshinm.WebSandboxMC.bridge;

import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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
    public void sendWorld(Channel channel) {

        for (int i = -radius; i < radius; ++i) {
            for (int j = -radius; j < radius; ++j) {
                for (int k = -radius; k < radius; ++k) {
                    Block block = world.getBlockAt(i + x_center, j + y_center, k + z_center);
                    int type = toWebBlockType(block.getType());

                    webSocketServerThread.sendLine(channel, "B,0,0," + (i + radius) + "," + (j + radius + y_offset) + "," + (k + radius) + "," + type);
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
    public void clientBlockUpdate(ChannelHandlerContext ctx, int x, int y, int z, int type) {
        Material material = toBukkitBlockType(type);
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
        block.setType(material);

        // Notify other web clients - note they will have the benefit of seeing the untranslated block (feature or bug?)
        webSocketServerThread.broadcastLineExcept(ctx.channel().id(), "B,0,0," + x + "," + y + "," + z + "," + type);
        webSocketServerThread.broadcastLineExcept(ctx.channel().id(), "R,0,0");
    }

    // Handle the bukkit world changing a block, tell all web clients
    public void notifyBlockUpdate(Location location, Material material) {
        //System.out.println("bukkit block ("+x+","+y+","+z+") was set to "+material);

        if (!withinSandboxRange(location)) {
            // Clients don't need to know about every block change on the server, only within the sandbox
            return;
        }


        // Send to all web clients to let them know it changed using the "B," command
        int type = toWebBlockType(material);

        int x = toWebLocationBlockX(location);
        int y = toWebLocationBlockY(location);
        int z = toWebLocationBlockZ(location);

        webSocketServerThread.broadcastLine("B,0,0,"+x+","+y+","+z+","+type);
        webSocketServerThread.broadcastLine("R,0,0");

        System.out.println("notified block update: ("+x+","+y+","+z+") to "+type);
    }

    // Translate web<->bukkit blocks
    // TODO: refactor to remove all bukkit dependency in this class (enums strings?), generalize to can support others
    private int toWebBlockType(Material material) {
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
            default: material = Material.DIAMOND_ORE; // placeholder TODO fix
        }
        return material;
    }
}
