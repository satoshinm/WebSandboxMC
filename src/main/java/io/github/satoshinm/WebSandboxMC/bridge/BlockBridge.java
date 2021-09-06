package io.github.satoshinm.WebSandboxMC.bridge;

import io.github.satoshinm.WebSandboxMC.Settings;
import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import io.netty.buffer.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.material.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.zip.DeflaterOutputStream;

/**
 * Bridges blocks in the world, translates between coordinate systems
 */
public class BlockBridge {

    public WebSocketServerThread webSocketServerThread;
    private final int x_center, y_center, z_center, y_offset;
    public final int radius;
    public final World world;
    public Location spawnLocation;
    private boolean allowBreakPlaceBlocks;
    private boolean allowSigns;
    private boolean seeTime;
    private Map<Material, Integer> blocksToWeb;
    private int blocksToWebMissing; // unknown/unsupported becomes cloud, if key missing
    private boolean warnMissing;
    private List<Material> unbreakableBlocks;
    private String textureURL;
    private boolean creativeMode;

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
        this.seeTime = settings.seeTime;

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
        this.creativeMode = settings.creativeMode;
    }

    private final ByteBufAllocator allocator = PooledByteBufAllocator.DEFAULT;

    // Send the client the initial section of the world when they join
    public void sendWorld(final Channel channel) {
        if (textureURL != null) {
            webSocketServerThread.sendLine(channel, "t," + textureURL);
        }

        if (creativeMode) {
            webSocketServerThread.sendLine(channel, "m,1");
        } else {
            webSocketServerThread.sendLine(channel, "m,0");
        }

        String name = webSocketServerThread.webPlayerBridge.channelId2name.get(channel.id());
        webSocketServerThread.sendLine(channel, "u," + name);

        int day_length = 60 * 20; // 20 minutes

        if (seeTime) {
            double fraction = world.getTime() / 1000.0 / 24.0; // 0-1
            double elapsed = (fraction + 6.0 / 24) * day_length;
            webSocketServerThread.sendLine(channel, "E," + elapsed + "," + day_length);
            // TODO: listen for server time change and resend E, command
        } else {
            webSocketServerThread.sendLine(channel, "E,0,0");
        }

        // Send a multi-block update message announcement that a binary chunk is coming
        /*
        int startx = -radius;
        int starty = y_offset;
        int startz = -radius;
        int endx = radius - 1;
        int endy = radius * 2 - 1 + y_offset;
        int endz = radius - 1;
        */
        int startx = 0;
        int starty = y_offset;
        int startz = 0;
        int endx = radius * 2 - 1;
        int endy = radius * 2 - 1 + y_offset;
        int endz = radius * 2 - 1;

        webSocketServerThread.sendLine(channel, "b," + startx + "," + starty + "," + startz + "," + endx + "," + endy + "," + endz);

        ByteBuf data = allocator.buffer( (radius*2) * (radius*2) * (radius*2) * 2);

        boolean thereIsAWorld = false;
        LinkedList<String> blockDataUpdates = new LinkedList<String>();
        int offset = 0;
        // Gather block data for multiblock update compression
        for (int i = -radius; i < radius; ++i) {
            for (int j = -radius; j < radius; ++j) {
                for (int k = -radius; k < radius; ++k) {
                    Block block = world.getBlockAt(j + x_center, i + y_center, k + z_center);

                    Material material = block.getType();
                    BlockState blockState = block.getState();

                    int type = toWebBlockType(material, blockState);
                    data.setShortLE(offset, (short) type);
                    offset += 2;

                    // Gather block data updates
                    String blockDataCommand = getDataBlockUpdateCommand(block.getLocation(), material, blockState);

                    if (type != 0) thereIsAWorld = true;
                    if (blockDataCommand != null) blockDataUpdates.add(blockDataCommand);
                }
            }
        }
        data.writerIndex(data.capacity());

        // Send compressed block types
        try {
            // Compress with DeflateOutputStream, note _not_ GZIPOutputStream since that adds
            // gzip headers (see https://stackoverflow.com/questions/1838699/how-can-i-decompress-a-gzip-stream-with-zlib)
            // which miniz does not support (https://github.com/richgel999/miniz/blob/ec028ffe66e2da67eed208de3db66fcf72b24dac/miniz.h#L33)
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            DeflaterOutputStream gzipOutputStream = new DeflaterOutputStream(byteArrayOutputStream);
            byte[] bytes = new byte[data.readableBytes()];
            data.readBytes(bytes);
            gzipOutputStream.write(bytes);
            gzipOutputStream.close();

            byte[] gzipBytes = byteArrayOutputStream.toByteArray();
            ByteBuf gzipBytesBuffer = Unpooled.wrappedBuffer(gzipBytes);
            webSocketServerThread.sendBinary(channel, gzipBytesBuffer);
        } catch (IOException ex) {
            webSocketServerThread.log(Level.WARNING, "Failed to compress chunk data to send to web client: "+ex);
            throw new RuntimeException(ex);
        } finally {
            data.release();
        }

        // then block data and refresh
        for (String blockDataCommand : blockDataUpdates) {
            webSocketServerThread.sendLine(channel, blockDataCommand);
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

    // Get the command string to send block data besides the type, if needed (signs, lighting)
    private String getDataBlockUpdateCommand(Location location, Material material, BlockState blockState) {
        if (material == null || material == Material.AIR) return null;

        int light_level = toWebLighting(material, blockState);
        if (light_level != 0) {
            int x = toWebLocationBlockX(location);
            int y = toWebLocationBlockY(location);
            int z = toWebLocationBlockZ(location);
            return "L,0,0,"+x+","+y+","+z+"," + light_level;
        }

        if (blockState instanceof Sign) {
            Block block = location.getWorld().getBlockAt(location);
            Sign sign = (Sign) blockState;

            return getNotifySignChange(block.getLocation(), block.getType(), block.getState(), sign.getLines());
        }

        return null;
    }

    private void setBlockUpdate(Location location, Material material, BlockState blockState) {
        // Send to all web clients to let them know it changed using the "B," command
        int type = toWebBlockType(material, blockState);

        if (type == -1) {
            if (warnMissing) {
                webSocketServerThread.log(Level.WARNING, "Block type missing from blocks_to_web: " + material + " at " + location);
            }
            type = blocksToWebMissing;
        }

        int x = toWebLocationBlockX(location);
        int y = toWebLocationBlockY(location);
        int z = toWebLocationBlockZ(location);

        webSocketServerThread.broadcastLine("B,0,0,"+x+","+y+","+z+","+type);
        String blockDataCommand = this.getDataBlockUpdateCommand(location, material, blockState);
        if (blockDataCommand != null) {
            webSocketServerThread.broadcastLine(blockDataCommand);
        }

        webSocketServerThread.log(Level.FINEST, "notified block update: ("+x+","+y+","+z+") to "+type);
    }

    private int toWebLighting(Material material, BlockState blockState) {
        BlockData blockData = blockState.getBlockData();
        boolean isLit = false;

        if (blockData instanceof Lightable) {
            Lightable lightable = (Lightable) blockData;
            isLit = lightable.isLit();
        }
        // See http://minecraft.gamepedia.com/Light#Blocks
        // Note not all of these may be fully supported yet
        switch (material) {
            case BEACON:
            case FIRE:
            case GLOWSTONE:
            case JACK_O_LANTERN:
            case LAVA:
            case REDSTONE_LAMP: // TODO: get notified when toggles on/off
                if (!isLit) {
                    return 0;
                }

            case SEA_LANTERN:
            case END_ROD:
                return 15;

            case TORCH:
                return 14;

            case FURNACE:
                if (blockData instanceof org.bukkit.block.data.type.Furnace) { // TODO: or is Lightable too?
                    org.bukkit.block.data.type.Furnace furnace = (org.bukkit.block.data.type.Furnace) blockData;
                    if (!furnace.isLit()) {
                        return 0;
                    }
                }
                return 13;

            case END_PORTAL:
            case NETHER_PORTAL:
                return 11;

            case REDSTONE_ORE:
            case DEEPSLATE_REDSTONE_ORE:
                if (!isLit) {
                    return 0;
                }
                return 9;

            case ENDER_CHEST:
            case REDSTONE_TORCH:
                if (!isLit) {
                    return 0;
                }
                return 7;

            case MAGMA_BLOCK:
                return 3;

            case BREWING_STAND:
            case BROWN_MUSHROOM:
            case DRAGON_EGG:
            case END_PORTAL_FRAME:
                return 1;
            default:
                return 0;
        }
    }

    // The web client represents directional blocks has four block ids
    // example: furnaces
    private int getDirectionalOrthogonalWebBlock(int base, BlockFace facing) {
        switch (facing) {
            case NORTH: return base+0;
            case SOUTH: return base+1;
            case WEST: return base+2;
            case EAST: return base+3;
            default:
                webSocketServerThread.log(Level.WARNING, "unknown orthogonal directional rotation: "+facing);
                return base;
        }
    }
    private int getDirectionalOrthogonalWebBlock(int base, Directional directional) {
        return this.getDirectionalOrthogonalWebBlock(base, directional.getFacing());
    }

    // example: pumpkins, for some reason, fronts are inverted
    private int getDirectionalOrthogonalWebBlockReversed(int base, BlockFace facing) {
        switch (facing) {
            case SOUTH: return base+0;
            case NORTH: return base+1;
            case EAST: return base+2;
            case WEST: return base+3;
            default:
                webSocketServerThread.log(Level.WARNING, "unknown orthogonal directional rotation: "+facing);
                return base;
        }
    }
    private int getDirectionalOrthogonalWebBlockReversed(int base, Directional directional) {
        return this.getDirectionalOrthogonalWebBlockReversed(base, directional);
    }

    // Translate web<->bukkit blocks
    // TODO: refactor to remove all bukkit dependency in this class (enums strings?), generalize to can support others
    private int toWebBlockType(Material material, BlockState blockState) {
        if (blocksToWeb.containsKey(material)) {
            return blocksToWeb.get(material);
        }

        BlockData blockData = blockState != null ? blockState.getBlockData() : null;

        Directional directional = null;
        if (blockData instanceof Directional) {
            directional = (Directional) blockData;
        }

        boolean isLit = false;
        if (blockData instanceof Lightable) {
            Lightable lightable = (Lightable) blockData;
            isLit = lightable.isLit();
        }

        switch (material) {
            case AIR: return 0;
            case GRASS: return 1;
            case SAND: return 2;
            case SMOOTH_STONE: return 3;  // TODO: 3 is smooth stone brick, what is this?
            case MOSSY_STONE_BRICKS: return 76; // mossy stone brick
            case CRACKED_STONE_BRICKS: return 77; // cracked stone brick
            case BRICKS: return 4;

            case OAK_LOG: // log = block found in trees
            case OAK_WOOD: // wood = bark block
            case STRIPPED_OAK_LOG:
            case STRIPPED_OAK_WOOD:

            case JUNGLE_LOG:
            case JUNGLE_WOOD:
            case STRIPPED_JUNGLE_LOG:
            case STRIPPED_JUNGLE_WOOD:

            case ACACIA_LOG:
            case ACACIA_WOOD:
            case STRIPPED_ACACIA_LOG:
            case STRIPPED_ACACIA_WOOD:

            case DARK_OAK_LOG:
            case DARK_OAK_WOOD:
            case STRIPPED_DARK_OAK_LOG:
            case STRIPPED_DARK_OAK_WOOD:

                return 5; // oak wood log

            case SPRUCE_LOG:
            case STRIPPED_SPRUCE_LOG:
                return 107; // spruce wood log

            case BIRCH_LOG:
            case STRIPPED_BIRCH_LOG:
                return 108; // birch wood log

            // TODO: tree.getDirection(), faces different

            case GOLD_ORE: return 70;
            case IRON_ORE: return 71;
            case COAL_ORE: return 72;
            case LAPIS_ORE: return 73;
            case LAPIS_BLOCK: return 74;
            case DIAMOND_ORE: return 48;
            case REDSTONE_ORE: return 49;
            // TODO: more ores, for now, showing as stone
            case NETHER_QUARTZ_ORE: return 6;
            case STONE: return 6;
            case DIRT: return 7;

            case OAK_PLANKS:
            case SPRUCE_PLANKS:
            case BIRCH_PLANKS:
            case JUNGLE_PLANKS:
            case ACACIA_PLANKS:
            case DARK_OAK_PLANKS:
            case CRIMSON_PLANKS:
                return 8; // plank

            case SNOW: return 9;

            case GLASS: return 10;
            case COBBLESTONE: return 11;

            case CHEST: return 14;

            case OAK_LEAVES:
            case DARK_OAK_LEAVES:
            case ACACIA_LEAVES:
            case BIRCH_LEAVES:
                return 15; // leaves

            case SPRUCE_LEAVES: // "redwood
                return 109; // spruce leaves

            // TODO: return  cloud (16);

            case TALL_GRASS: return 17;

            // TODO: other double plants, but a lot look like longer long grass

            case FERN: return 29; // fern

            case DEAD_BUSH: return 23; // deadbush, places on sand
            case DANDELION: return 18;
            case POPPY:
            case ALLIUM:
            case AZURE_BLUET:
            case RED_TULIP:
            case ORANGE_TULIP:
            case PINK_TULIP:
            case OXEYE_DAISY:
            case CORNFLOWER:
            case LILY_OF_THE_VALLEY:
            case WITHER_ROSE:
            case SPORE_BLOSSOM:
                return 19; // red rose

            case CHORUS_FLOWER: return 20;

            case OAK_SAPLING:
            case DARK_OAK_SAPLING:
            case ACACIA_SAPLING:
            case JUNGLE_SAPLING:
                return 20; // oak sapling

            case SPRUCE_SAPLING:
                return 30; // spruce sapling ("darker barked/leaves tree species")

            case BIRCH_SAPLING:
                return 31; // birch sapling

            case SUNFLOWER: return 21;
            case WHITE_TULIP: return 22; // white flower
            case BLUE_ORCHID: return 23; // blue flower

            case WHITE_WOOL: return 32;
            case ORANGE_WOOL: return 33;
            case MAGENTA_WOOL: return 34;
            case LIGHT_BLUE_WOOL: return 35;
            case YELLOW_WOOL: return 36;
            case LIME_WOOL: return 37;
            case PINK_WOOL: return 38;
            case GRAY_WOOL: return 39;
            case LIGHT_GRAY_WOOL: return 40; // formerly silver
            case CYAN_WOOL: return 41;
            case PURPLE_WOOL: return 42;
            case BLUE_WOOL: return 43;
            case BROWN_WOOL: return 44;
            case GREEN_WOOL: return 45;
            case BLACK_WOOL: return 47;

            case OAK_WALL_SIGN:
            case SPRUCE_WALL_SIGN:
            case BIRCH_WALL_SIGN:
            case JUNGLE_WALL_SIGN:
            case DARK_OAK_WALL_SIGN:
            case CRIMSON_WALL_SIGN:
                return 0; // air, since text is written on block behind it

            case OAK_SIGN:
            case SPRUCE_SIGN:
            case BIRCH_SIGN:
            case JUNGLE_SIGN:
            case DARK_OAK_SIGN:
            case CRIMSON_SIGN:
                return 8; // plank TODO: return  sign post model

                // Light sources (nonzero toWebLighting()) TODO: return  different textures? + allow placement, distinct blocks
            case GLOWSTONE: return 64; // #define GLOWING_STONE
            case SEA_LANTERN: return 35; // light blue wool
            case TORCH: return 21; // sunflower, looks kinda like a torch
            case REDSTONE_TORCH:
            case REDSTONE_WALL_TORCH:
                return 19; // red flower, vaguely a torch

            // Liquids
            // TODO: flowing, stationary, and different heights of each liquid
            case WATER:
                return 12; // water
            case LAVA:
                return 13; // lava

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
            case CRAFTING_TABLE: return 53;
            case FURNACE: {
                if (directional != null) {
                    if (!isLit) {
                        return getDirectionalOrthogonalWebBlock(90, directional); // 90, 91, 92, 93
                    } else {
                        return getDirectionalOrthogonalWebBlock(94, directional); // 94, 95, 96, 97
                    }
                }
                return !isLit ? 90 : 94;
                //return !isLit ? 54 : 55; // old
            }

            case SPAWNER: return 56;
            case SNOW_BLOCK: return 57;
            case ICE: return 58;
            case CLAY: return 59;
            case JUKEBOX: return 60;
            case CACTUS: return 61;
            case MYCELIUM: return 62;
            case NETHERRACK: return 63;
            case SPONGE: return 24;
            case MELON: return 25;
            case END_STONE: return 26;
            case TNT: return 27;
            case EMERALD_BLOCK: return 28;
            case CARVED_PUMPKIN: {
                if (directional != null) {
                    return getDirectionalOrthogonalWebBlockReversed(98, directional); // 98, 99, 100, 101
                }
            }
            case PUMPKIN:
                return 78; // faceless

            case JACK_O_LANTERN: {
                if (directional != null) {
                    return getDirectionalOrthogonalWebBlockReversed(102, directional); // 102, 103, 104, 105
                }

                return 79; // all faces
            }
            case BROWN_MUSHROOM_BLOCK: return 80; // brown TODO: data
            case RED_MUSHROOM_BLOCK: return 81; // red TODO: data
            case COMMAND_BLOCK: return 82;
            case EMERALD_ORE: return 83;
            case SOUL_SAND: return 84;
            case NETHER_BRICK: return 85;
            case FARMLAND: return 86; // wet farmland TODO: dry farmland (87)
            case REDSTONE_LAMP:
                return !isLit ? 88 : 89;

            case BARRIER: return 106;
            default: return this.blocksToWebMissing;
        }
    }

    // Mutate blockState to block of type type
    private void toBukkitBlockType(int type, BlockState blockState) {
        Material material = null;
        BlockData blockData = null;

        switch (type) {
            case 0: material = Material.AIR; break;
            case 1: material = Material.GRASS; break;
            case 2: material = Material.SAND; break;
            case 3: material = Material.SMOOTH_STONE; break; // TODO: what is smooth brick?
            case 4: material = Material.BRICK; break;
            case 5: material = Material.OAK_LOG; break;
            case 6: material = Material.STONE; break;
            case 7: material = Material.DIRT; break;
            case 8: material = Material.OAK_WOOD; break; // TODO: this was WOOD, is this right? bark?
            case 9: material = Material.SNOW_BLOCK; break;
            case 10: material = Material.GLASS; break;
            case 11: material = Material.COBBLESTONE; break;
            case 12: material = Material.WATER; break;
            case 13: material = Material.LAVA; break;
            case 14: material = Material.CHEST; break; // TODO: doesn't seem to set?
            case 15: material = Material.OAK_LEAVES; break;
            //case 16: material = Material.clouds; break; // clouds
            case 17: material = Material.TALL_GRASS; break;
            case 18: material = Material.DANDELION; break;
            case 19: material = Material.RED_TULIP; break;
            case 20: material = Material.CHORUS_FLOWER; break;
            case 21: material = Material.RED_MUSHROOM; break;
            case 22: material = Material.BROWN_MUSHROOM; break;
            case 23: material = Material.DEAD_BUSH; break;
            case 24: material = Material.SPONGE; break;
            case 25: material = Material.MELON; break;
            case 26: material = Material.END_STONE; break;
            case 27: material = Material.TNT; break;
            case 28: material = Material.EMERALD_BLOCK; break;

            case 29: material = Material.FERN; break;
            case 30: material = Material.SPRUCE_SAPLING; break;
            case 31: material = Material.BIRCH_SAPLING; break;

            case 32: material = Material.WHITE_WOOL; break;
            case 33: material = Material.ORANGE_WOOL; break;
            case 34: material = Material.MAGENTA_WOOL; break;
            case 35: material = Material.LIGHT_BLUE_WOOL; break;
            case 36: material = Material.YELLOW_WOOL; break;
            case 37: material = Material.LIME_WOOL; break;
            case 38: material = Material.PINK_WOOL; break;
            case 39: material = Material.GRAY_WOOL; break;
            case 40: material = Material.LIGHT_GRAY_WOOL; break;
            case 41: material = Material.CYAN_WOOL; break;
            case 42: material = Material.PURPLE_WOOL; break;
            case 43: material = Material.BLUE_WOOL; break;
            case 44: material = Material.BROWN_WOOL; break;
            case 45: material = Material.GREEN_WOOL; break;
            case 46: material = Material.RED_WOOL; break;
            case 47: material = Material.BLACK_WOOL; break;

            case 48: material = Material.DIAMOND_ORE; break;
            case 49: material = Material.REDSTONE_ORE; break;
            case 50: material = Material.BOOKSHELF; break;
            case 51: material = Material.MOSSY_COBBLESTONE; break;
            case 52: material = Material.OBSIDIAN; break;
            case 53: material = Material.CRAFTING_TABLE; break;

            case 54: // TODO: remove, what was this?
            case 55: // TODO: remove, what was this?
                material = Material.FURNACE;
                break;

            case 56: material = Material.AIR; break; // not allowing, dangerous Material.MOB_SPAWNER
            case 57: material = Material.SNOW_BLOCK; break;
            case 58: material = Material.ICE; break;
            case 59: material = Material.CLAY; break;
            case 60: material = Material.JUKEBOX; break;
            case 61: material = Material.CACTUS; break;
            case 62: material = Material.MYCELIUM; break;
            case 63: material = Material.NETHERRACK; break;
            case 64: material = Material.GLOWSTONE; break;
            case 65: material = Material.BEDROCK; break;
            case 66: material = Material.GRAVEL; break;
            case 67: material = Material.IRON_BLOCK; break;
            case 68: material = Material.GOLD_BLOCK; break;
            case 69: material = Material.DIAMOND_BLOCK; break;
            case 70: material = Material.GOLD_ORE; break;
            case 71: material = Material.IRON_ORE; break;
            case 72: material = Material.COAL_ORE; break;
            case 73: material = Material.LAPIS_ORE; break;
            case 74: material = Material.LAPIS_BLOCK; break;
            case 75: material = Material.SANDSTONE; break;
            case 76: material = Material.MOSSY_COBBLESTONE; break; // TODO: mossy stone brick
            case 77: material = Material.MOSSY_COBBLESTONE; break; // TODO: cracked stone brick
            case 78: material = Material.PUMPKIN; break; // TODO: direction
            case 79: material = Material.JACK_O_LANTERN; break; // TODO: direction
            case 80: material = Material.BROWN_MUSHROOM_BLOCK; break; // TODO: type
            case 81: material = Material.RED_MUSHROOM_BLOCK; break; // TODO: type
            case 82: material = Material.AIR; break; // not ever allowing Material.COMMAND; break;
            case 83: material = Material.EMERALD_ORE; break;
            case 84: material = Material.SOUL_SAND; break;
            case 85: material = Material.NETHER_BRICK; break;
            case 86: material = Material.FARMLAND; break;
            //case 87: // TODO: dry farmland
            case 88:
            case 89:
                material = Material.REDSTONE_LAMP;
                // TODO: set lit
                //Lightable lit = new Lightable();
                //lit.setLit(type == 89); // lamp on
                break;

            case 90:
            case 91:
            case 92:
            case 93:

            case 94:
            case 95:
            case 96:
            case 97:
                material = Material.FURNACE; // TODO: direction
                // TODO: set lit
                //org.bukkit.block.data.type.Furnace f = new org.bukkit.block.data.type.Furnace(); // TODO: how to construct this? it is an interface, do we have to get after?
                //f.setLit(type >= 94); // burning
                // TODO: set direction
                break;

            case 98:
            case 99:
            case 100:
            case 101:
                material = Material.PUMPKIN; break; // TODO: direction

            case 102:
            case 103:
            case 104:
            case 105:
                material = Material.JACK_O_LANTERN; break; // TODO: direction

            case 106: material = Material.AIR; break; // not allowing Material.BARRIER; break;
            case 107: material = Material.SPRUCE_WOOD; break; // spruce wood log
            case 108: material = Material.BIRCH_WOOD; break; // birch wood log
            case 109: material = Material.SPRUCE_LEAVES; break; // TODO: spruce leaves

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

            if (blockData != null) {
                blockState.setBlockData(blockData);
            }

            boolean force = true;
            boolean applyPhysics = false;
            blockState.update(force, applyPhysics);
        }
    }

    public String getNotifySignChange(Location location, Material material, BlockState blockState, String[] lines) {
        int x = toWebLocationBlockX(location);
        int y = toWebLocationBlockY(location);
        int z = toWebLocationBlockZ(location);
        BlockFace blockFace = BlockFace.NORTH;
        if (blockState.getData() instanceof org.bukkit.material.Sign) {
            org.bukkit.material.Sign sign = (org.bukkit.material.Sign) blockState.getData();
            try {
                blockFace = sign.getFacing();
            } catch (NullPointerException ex) {
                // ignore invalid data, https://github.com/GlowstoneMC/Glowstone/issues/484
                webSocketServerThread.log(Level.WARNING, "Invalid sign data at " + location);
            }
        }

        int face = 7;
        if (blockFace == null) {
            // https://github.com/satoshinm/WebSandboxMC/issues/92
            webSocketServerThread.log(Level.WARNING, "Invalid sign face at " + location);

        } else if (material == Material.OAK_WALL_SIGN ||
                material == Material.SPRUCE_WALL_SIGN ||
                material == Material.BIRCH_WALL_SIGN ||
                material == Material.JUNGLE_WALL_SIGN ||
                material == Material.DARK_OAK_WALL_SIGN ||
                material == Material.CRIMSON_WALL_SIGN) {
            // wallsigns, attached to block behind
            switch (blockFace) {
                default:
                case NORTH:
                    face = 2; // north
                    z += 1;
                    break;
                case SOUTH:
                    face = 3; // south
                    z -= 1;
                    break;
                case WEST:
                    face = 0; // west
                    x += 1;
                    break;
                case EAST:
                    face = 1; // east
                    x -= 1;
                    break;
            }
    } else if (material == Material.OAK_SIGN ||
            material == Material.SPRUCE_SIGN ||
            material == Material.BIRCH_SIGN ||
            material == Material.JUNGLE_SIGN ||
            material == Material.DARK_OAK_SIGN ||
            material == Material.CRIMSON_SIGN) {
            // standing sign, on the block itself
            // TODO: support more fine-grained directions, right now Craft only four cardinal
            switch (blockFace) {
                case SOUTH:
                case SOUTH_SOUTH_WEST:
                case SOUTH_WEST:
                    face = 3; // south
                    break;

                case WEST_SOUTH_WEST:
                case WEST:
                case WEST_NORTH_WEST:
                case NORTH_WEST:
                    face = 0; // west
                    break;

                default:
                case NORTH_NORTH_WEST:
                case NORTH:
                case NORTH_NORTH_EAST:
                case NORTH_EAST:
                    face = 2; // north
                    break;

                case EAST_NORTH_EAST:
                case EAST:
                case EAST_SOUTH_EAST:
                case SOUTH_EAST:
                case SOUTH_SOUTH_EAST:
                    face = 1; // east
                    break;
            }
        }

        webSocketServerThread.log(Level.FINEST, "sign change: "+location+", blockFace="+blockFace);
        String text = "";
        for (int i = 0; i < lines.length; ++i) {
            text += lines[i] + " "; // TODO: support explicit newlines; Craft wraps sign text lines automatically
        }
        if (text.contains("\n")) {
            // \n is used as a command terminator in the Craft protocol (but ',' is acceptable)
            text = text.replaceAll("\n", " ");
        }

        return "S,0,0,"+x+","+y+","+z+","+face+","+text;
    }

    public void notifySignChange(Location location, Material material, BlockState blockState, String[] lines) {
        webSocketServerThread.broadcastLine(this.getNotifySignChange(location, material, blockState, lines));
        webSocketServerThread.broadcastLine("R,0,0"); // TODO: refresh correct chunk
    }

    public void clientNewSign(ChannelHandlerContext ctx, int x, int y, int z, int face, String text) {
        if (!allowSigns) {
            webSocketServerThread.sendLine(ctx.channel(), "T,Writing on signs is not allowed");
            // TODO: revert on client
            return;
        }

        BlockFace blockFace;
        switch (face) {
            case 0: // west
                blockFace = BlockFace.WEST;
                x -= 1;
                break;
            case 1: // east
                blockFace = BlockFace.EAST;
                x += 1;
                break;
            default:
            case 2: // north
                blockFace = BlockFace.NORTH;
                z -= 1;
                break;
            case 3: // south
                blockFace = BlockFace.SOUTH;
                z += 1;
                break;
        }
        org.bukkit.material.Sign signDirection = new org.bukkit.material.Sign();
        signDirection.setFacingDirection(blockFace);

        Location location = toBukkitLocation(x, y, z);
        if (!withinSandboxRange(location)) {
            webSocketServerThread.log(Level.FINEST, "client tried to write a sign outside sandbox range");
            return;
        }

        // Create the sign
        Block block = location.getWorld().getBlockAt(location);
        /*
        block.setTypeIdAndData(Material.WALL_SIGN.getId(), data, applyPhysics);
        webSocketServerThread.log(Level.FINEST, "setting sign at "+location+" data="+data);
        */
        BlockState blockState = block.getState();
        blockState.setType(Material.OAK_WALL_SIGN);
        blockState.setData(signDirection);
        boolean force = true;
        boolean applyPhysics = false;
        blockState.update(force, applyPhysics);
        webSocketServerThread.log(Level.FINEST, "setting sign at "+location+" blockFace="+blockFace);

        // Set the sign text
        blockState = block.getState();
        if (!(blockState instanceof Sign)) {
            webSocketServerThread.log(Level.WARNING, "failed to place sign at "+location);
            return;
        }
        Sign sign = (Sign) blockState;

        // TODO: text lines by 15 characters into 5 lines
        sign.setLine(0, text);
        sign.update(force, applyPhysics);
        webSocketServerThread.log(Level.FINEST, "set sign text="+text+", signDirection="+signDirection+", blockFace="+blockFace+", block="+block+", face="+face);

        // SignChangeEvent not posted when signs created programmatically; notify web clients ourselves
        notifySignChange(location, block.getType(), block.getState(), sign.getLines());
    }
}
