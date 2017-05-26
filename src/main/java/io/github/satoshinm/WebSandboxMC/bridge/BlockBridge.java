package io.github.satoshinm.WebSandboxMC.bridge;

import io.github.satoshinm.WebSandboxMC.Settings;
import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.block.Sign;
import org.bukkit.material.*;

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
    private int blocksToWebMissing; // unknown/unsupported becomes cloud, if key missing
    private boolean warnMissing;
    private List<Material> unbreakableBlocks;
    private String textureURL;

    public BlockBridge(WebSocketServerThread webSocketServerThread, Settings settings) {
        this.webSocketServerThread = webSocketServerThread;

        this.radius = settings.radius;

        this.y_offset = settings.y_offset;

        if (settings.world == null || "".equals(settings.world)) {
            this.world = Bukkit.getWorlds().get(0);
        } else {
            this.world = Bukkit.getWorld(settings.world);
        }
        if (this.world == null) {
            throw new IllegalArgumentException("World not found: " + settings.world);
        }

        if (settings.x_center == 0 && settings.y_center == 0 && settings.z_center == 0) {
            Location spawn = this.world.getSpawnLocation();
            this.x_center = spawn.getBlockX();
            this.y_center = spawn.getBlockY();
            this.z_center = spawn.getBlockZ();
        } else {
            this.x_center = settings.x_center;
            this.y_center = settings.y_center;
            this.z_center = settings.z_center;
        }

        // TODO: configurable spawn within range of sandbox, right now, it is the center of the sandbox
        this.spawnLocation = new Location(this.world, this.x_center, this.y_center, this.z_center);

        this.allowBreakPlaceBlocks = settings.allowBreakPlaceBlocks;
        this.allowSigns = settings.allowSigns;

        this.blocksToWeb = new HashMap<Material, Integer>();
        this.blocksToWebMissing = 16; // unknown/unsupported becomes cloud

        // Overrides from config, if any
        for (String materialString : settings.blocksToWebOverride.keySet()) {
            Object object = settings.blocksToWebOverride.get(materialString);

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

        this.warnMissing = settings.warnMissing;

        this.unbreakableBlocks = new ArrayList<Material>();
        for (String materialString : settings.unbreakableBlocks) {
            Material material = Material.getMaterial(materialString);
            if (material == null) {
                webSocketServerThread.log(Level.WARNING, "unbreakable_blocks invalid material ignored: " + materialString);
                continue;
            }
            this.unbreakableBlocks.add(material);
        }

        this.textureURL = settings.textureURL;
    }

    // Send the client the initial section of the world when they join
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
                    thereIsAWorld |= setBlockUpdate(block.getLocation(), block.getType(), block.getState());
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
    public void clientBlockUpdate(ChannelHandlerContext ctx, int x, int y, int z, int type) {
        if (!allowBreakPlaceBlocks) {
            webSocketServerThread.sendLine(ctx.channel(), "T,Breaking/placing blocks not allowed");
            // TODO: set back to original block to revert on client
            return;
        }

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
        if (unbreakableBlocks.contains(previousMaterial)) {
            webSocketServerThread.log(Level.FINEST, "client tried to change unbreakable block at " +
                    location + " of type previousMaterial="+previousMaterial);

            webSocketServerThread.sendLine(ctx.channel(), "T,You cannot break blocks of type " + previousMaterial);

            // Revert on client
            int previousType = toWebBlockType(previousMaterial, null);
            webSocketServerThread.sendLine(ctx.channel(), "B,0,0,"+x+","+y+","+z+","+previousType);
            webSocketServerThread.sendLine(ctx.channel(), "R,0,0");
            return;
        }

        Block block = world.getBlockAt(location);
        if (block == null) {
            webSocketServerThread.log(Level.WARNING, "web client no such block at " + location); // does this happen?
            return;
        }

        webSocketServerThread.log(Level.FINEST, "setting block at "+location);

        BlockState blockState = block.getState();
        toBukkitBlockType(type, blockState);

        // Notify other web clients - note they will have the benefit of seeing the untranslated block (feature or bug?)
        webSocketServerThread.broadcastLineExcept(ctx.channel().id(), "B,0,0," + x + "," + y + "," + z + "," + type);
        webSocketServerThread.broadcastLineExcept(ctx.channel().id(), "R,0,0");
    }


    // Handle the bukkit world changing a block, tell all web clients and refresh
    public void notifyBlockUpdate(Location location, Material material, BlockState blockState) {
        webSocketServerThread.log(Level.FINEST, "bukkit block at "+location+" was set to "+material);

        if (!withinSandboxRange(location)) {
            // Clients don't need to know about every block change on the server, only within the sandbox
            return;
        }

        setBlockUpdate(location, material, blockState);

        webSocketServerThread.broadcastLine("R,0,0");
    }

    private boolean setBlockUpdate(Location location, Material material, BlockState blockState) {
        // Send to all web clients to let them know it changed using the "B," command
        int type = toWebBlockType(material, blockState);
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

        int light_level = toWebLighting(material, blockState);
        if (light_level != 0) {
            webSocketServerThread.broadcastLine("L,0,0,"+x+","+y+","+z+"," + light_level);
        }

        if (material == Material.WALL_SIGN || material == Material.SIGN_POST) {
            Block block = location.getWorld().getBlockAt(location);
            if (blockState instanceof Sign) {
                Sign sign = (Sign) blockState;

                notifySignChange(block.getLocation(), block.getType(), block.getState(), sign.getLines());
            }
        }

        webSocketServerThread.log(Level.FINEST, "notified block update: ("+x+","+y+","+z+") to "+type);

        return substantial; // was something "real" set? (not air, not missing)
    }

    private int toWebLighting(Material material, BlockState blockState) {
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
            default:
                return 0;
        }
    }

    // The web client represents directional blocks has four block ids
    // example: furnaces
    private int getDirectionalOrthogonalWebBlock(int base, Directional directional) {
        switch (directional.getFacing()) {
            case NORTH: return base+0;
            case SOUTH: return base+1;
            case WEST: return base+2;
            case EAST: return base+3;
            default:
                webSocketServerThread.log(Level.WARNING, "unknown orthogonal directional rotation: "+directional.getFacing());
                return base;
        }
    }
    // example: pumpkins, for some reason, fronts are inverted
    private int getDirectionalOrthogonalWebBlockReversed(int base, Directional directional) {
        switch (directional.getFacing()) {
            case SOUTH: return base+0;
            case NORTH: return base+1;
            case EAST: return base+2;
            case WEST: return base+3;
            default:
                webSocketServerThread.log(Level.WARNING, "unknown orthogonal directional rotation: "+directional.getFacing());
                return base;
        }
    }

    // Translate web<->bukkit blocks
    // TODO: refactor to remove all bukkit dependency in this class (enums strings?), generalize to can support others
    private int toWebBlockType(Material material, BlockState blockState) {
        if (blocksToWeb.containsKey(material)) {
            return blocksToWeb.get(material);
        }

        MaterialData materialData = blockState != null ? blockState.getData() : null;

        switch (material) {
            case AIR: return 0;
            case GRASS: return 1;
            case SAND: return 2;
            case SMOOTH_BRICK: {
                if (materialData instanceof TexturedMaterial) {
                    TexturedMaterial texturedMaterial = (TexturedMaterial) materialData;
                    switch (texturedMaterial.getMaterial()) {
                        case STONE: return 3; // stone brick
                        case MOSSY_COBBLESTONE: return 76; // mossy stone brick
                        case COBBLESTONE: return 77; // cracked stone brick
                    }
                }
                return 3; // stone brick
            }
            case BRICK: return 4;
            case LOG: return 5;
            case LOG_2: return 5; // wood

            case GOLD_ORE: return 70;
            case IRON_ORE: return 71;
            case COAL_ORE: return 72;
            case LAPIS_ORE: return 73;
            case LAPIS_BLOCK: return 74;
            case DIAMOND_ORE: return 48;
            case REDSTONE_ORE: return 49;
            // TODO: more ores, for now, showing as stone
            case QUARTZ_ORE: return 6;
            case STONE: return 6;
            case DIRT: return 7;
            case WOOD: return 8; // plank
            case SNOW: return 9;

            case GLASS: return 10;
            case COBBLESTONE: return 11;
            // TODO: return  light stone (12);
            // TODO: return  dark stone (13);
            case CHEST: return 14;
            case LEAVES: return 15;
            case LEAVES_2: return 15;
            // TODO: return  cloud (16);
            case DOUBLE_PLANT: return 17;  // TODO: other double plants, but a lot look like longer long grass
            case LONG_GRASS: {
                if (materialData instanceof LongGrass) {
                    LongGrass longGrass = (LongGrass) materialData;
                    GrassSpecies grassSpecies = longGrass.getSpecies();
                    switch (grassSpecies) {
                        default:
                        case NORMAL: return 17; // tall grass
                        case DEAD: return 23; // "shrub", places on grass, same texture as deadbush
                        // http://minecraft.gamepedia.com/Dead_Bush#Trivia "There is a variant of grass called "Shrub"
                        // that looks identical to the dead bush, but will occasionally drop seeds and is randomly
                        // offset from the center of the block like grass."

                        case FERN_LIKE: return 29; // fern
                    }
                }
                return 17; // tall grass
            }
            case DEAD_BUSH: return 23; // deadbush, places on sand
            case YELLOW_FLOWER: return 18;
            case RED_ROSE: return 19;
            //TODO case CHORUS_FLOWER: return 20;
            case SAPLING: {
                if (materialData instanceof Sapling) {
                    Sapling sapling = (Sapling) materialData;
                    switch (sapling.getSpecies()) {
                        default:
                        case GENERIC: return 20; // oak sapling
                        case REDWOOD: return 30; // spruce sapling ("darker barked/leaves tree species")
                        case BIRCH: return 31; // birch sapling
                    }
                }
                return 20; // oak sapling
            }
            // TODO: return  sunflower (21);
            // TODO: return  white flower (22);
            // TODO: return  blue flower (23);

            case WOOL:
            {
                if (materialData instanceof Wool) {
                    Wool wool = (Wool) materialData;
                    switch (wool.getColor()) {
                        case WHITE: return 32;
                        case ORANGE: return 33;
                        case MAGENTA: return 34;
                        case LIGHT_BLUE: return 35;
                        case YELLOW: return 36;
                        case LIME: return 37;
                        case PINK: return 38;
                        case GRAY: return 39;
                        case SILVER: return 40; // light gray
                        case CYAN: return 41;
                        case PURPLE: return 42;
                        case BLUE: return 43;
                        case BROWN: return 44;
                        case GREEN: return 45;
                        case RED: return 46;
                        default:
                        case BLACK: return 47;
                    }
                }
                return 47;

            }

            case WALL_SIGN: return 0; // air, since text is written on block behind it
            case SIGN_POST: return 8; // plank TODO: return  sign post model

            // Light sources (nonzero toWebLighting()) TODO: return  different textures? + allow placement, distinct blocks
            case GLOWSTONE: return 64; // #define GLOWING_STONE
            case SEA_LANTERN: return 35; // light blue wool
            case TORCH: return 21; // sunflower, looks kinda like a torch
            case REDSTONE_TORCH_OFF: return 19;
            case REDSTONE_TORCH_ON: return 19; // red flower, vaguely a torch

            // Liquids - currently using color blocks as placeholders since they appear too often
            case STATIONARY_WATER: return 35; // light blue wool
            case WATER: return 35; // light blue wool
            case STATIONARY_LAVA: return 35; // orange wool
            case LAVA: return 35; // orange wool

            // TODO: support more blocks by default
            case BEDROCK: return 65;
            case GRAVEL: return 66;
            case IRON_BLOCK: return 67;
            case GOLD_BLOCK: return 68;
            case DIAMOND_BLOCK: return 69;
            case SANDSTONE: return 75;
            case BOOKSHELF: return 50;
            case MOSSY_COBBLESTONE: return 51;
            case OBSIDIAN: return 52;
            case WORKBENCH: return 53;
            case FURNACE: {
                if (materialData instanceof org.bukkit.material.Furnace) {
                    org.bukkit.material.Furnace furnace = (org.bukkit.material.Furnace) materialData;
                    return getDirectionalOrthogonalWebBlock(90, furnace); // 90, 91, 92, 93
                }
                return 90;
                //return 54; // old
            }
            case BURNING_FURNACE: { // TODO: refactor with above, same code! different base block
                if (materialData instanceof org.bukkit.material.Furnace) {
                    org.bukkit.material.Furnace furnace = (org.bukkit.material.Furnace) materialData;
                    return getDirectionalOrthogonalWebBlock(94, furnace); // 94, 95, 96, 97
                }
                return 94;
                //return 55; // old
            }
            case MOB_SPAWNER: return 56;
            case SNOW_BLOCK: return 57;
            case ICE: return 58;
            case CLAY: return 59;
            case JUKEBOX: return 60;
            case CACTUS: return 61;
            case MYCEL: return 62;
            case NETHERRACK: return 63;
            case SPONGE: return 24;
            case MELON_BLOCK: return 25;
            case ENDER_STONE: return 26;
            case TNT: return 27;
            case EMERALD_BLOCK: return 28;
            case PUMPKIN: {
                if (materialData instanceof Pumpkin) {
                    Pumpkin pumpkin = (Pumpkin) materialData;
                    return getDirectionalOrthogonalWebBlockReversed(98, pumpkin); // 98, 99, 100, 101
                }

                return 78; // faceless
            }
            case JACK_O_LANTERN: {
                if (materialData instanceof Pumpkin) {
                    Pumpkin pumpkin = (Pumpkin) materialData;
                    return getDirectionalOrthogonalWebBlockReversed(102, pumpkin); // 102, 103, 104, 105
                }

                return 79; // all faces
            }
            case HUGE_MUSHROOM_1: return 80; // brown TODO: data
            case HUGE_MUSHROOM_2: return 81; // red TODO: data
            case COMMAND: return 82;
            case EMERALD_ORE: return 83;
            case SOUL_SAND: return 84;
            case NETHER_BRICK: return 85;
            case SOIL: return 86; // wet farmland TODO: dry farmland (87)
            case REDSTONE_LAMP_OFF: return 88;
            case REDSTONE_LAMP_ON: return 89;
            default: return this.blocksToWebMissing;
        }
    }

    // Mutate blockState to block of type type
    private void toBukkitBlockType(int type, BlockState blockState) {
        Material material = null;
        MaterialData materialData = null;

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
            // TODO: 24-31

            case 32:
            case 33:
            case 34:
            case 35:
            case 36:
            case 37:
            case 38:
            case 39:
            case 40:
            case 41:
            case 42:
            case 43:
            case 44:
            case 45:
            case 46:
            case 47:
                material = Material.WOOL;
                DyeColor color;
                switch (type) {
                    default:
                    case 32: color = DyeColor.WHITE; break;
                    case 33: color = DyeColor.ORANGE; break;
                    case 34: color = DyeColor.MAGENTA; break;
                    case 35: color = DyeColor.LIGHT_BLUE; break;
                    case 36: color = DyeColor.YELLOW; break;
                    case 37: color = DyeColor.LIME; break;
                    case 38: color = DyeColor.PINK; break;
                    case 39: color = DyeColor.GRAY; break;
                    case 40: color = DyeColor.SILVER; break; // light gray
                    case 41: color = DyeColor.CYAN; break;
                    case 42: color = DyeColor.PURPLE; break;
                    case 43: color = DyeColor.BLUE; break;
                    case 44: color = DyeColor.BROWN; break;
                    case 45: color = DyeColor.GREEN; break;
                    case 46: color = DyeColor.RED; break;
                    case 47: color = DyeColor.BLACK; break;
                }
                materialData = new Wool(color);
                break;

            case 64: material = Material.GLOWSTONE; break;
            default:
                webSocketServerThread.log(Level.WARNING, "untranslated web block id "+type);
                material = Material.DIAMOND_ORE; // placeholder TODO fix
        }

        if (unbreakableBlocks.contains(material)) {
            webSocketServerThread.log(Level.WARNING, "client tried to place unplaceable block type "+type+ " from "+material);
            return; // ignore, not reverting
        }

        if (material != null) {
            blockState.setType(material);

            if (materialData != null) {
                blockState.setData(materialData);
            }

            boolean force = true;
            boolean applyPhysics = false;
            blockState.update(force, applyPhysics);
        }
    }

    public void notifySignChange(Location location, Material material, BlockState blockState, String[] lines) {
        int x = toWebLocationBlockX(location);
        int y = toWebLocationBlockY(location);
        int z = toWebLocationBlockZ(location);
        byte data = blockState.getData().getData();

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
        boolean applyPhysics = false;
        block.setTypeIdAndData(Material.WALL_SIGN.getId(), data, applyPhysics);
        webSocketServerThread.log(Level.FINEST, "setting sign at "+location+" data="+data);
        BlockState blockState = block.getState();
        if (!(blockState instanceof Sign)) {
            webSocketServerThread.log(Level.WARNING, "failed to place sign at "+location);
            return;
        }
        Sign sign = (Sign) blockState;

        // TODO: text lines by 15 characters into 5 lines
        sign.setLine(0, text);
        sign.update(false, applyPhysics);

        // SignChangeEvent not posted when signs created programmatically; notify web clients ourselves
        notifySignChange(location, block.getType(), block.getState(), sign.getLines());
    }
}
