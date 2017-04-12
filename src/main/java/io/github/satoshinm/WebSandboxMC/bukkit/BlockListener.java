
package io.github.satoshinm.WebSandboxMC.bukkit;

import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class BlockListener implements Listener {

    public WebSocketServerThread webSocketServerThread;
    public final int x_center, y_center, z_center, radius, y_offset;

    public BlockListener(WebSocketServerThread webSocketServerThread, int x_center, int y_center, int z_center, int radius, int y_offset) {
        this.webSocketServerThread = webSocketServerThread;

        this.x_center = x_center;
        this.y_center = y_center;
        this.z_center = z_center;

        this.radius = radius;

        this.y_offset = y_offset;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        webSocketServerThread.notifyBlockUpdate(x, y, z, Material.AIR);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        Material material = block.getType();

        webSocketServerThread.notifyBlockUpdate(x, y, z, material);
    }

    // TODO: BlockBurnEvent
    // TODO: BlockFadeEvent
    // TODO: BlockFormEvent
    // TODO: BlockGrowEvent
    // TODO: BlockMultiPlaceEvent
    // TODO: BlockPhysicsEvent
    // TODO: BlockPiston*Event
    // TODO: BlockRedstoneEvent
    // TODO: BlockSpreadEvent


    // TODO: move handler to a different class?
    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String formattedMessage = event.getFormat().format(event.getMessage());
        formattedMessage = "<" + event.getPlayer().getDisplayName() + "> " + formattedMessage; // TODO: why isn't getFormat() take care of this?

        webSocketServerThread.notifyChat(formattedMessage);
    }

    public int toWebBlockType(Material material) {
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
                type = 3;
                break;
            case BRICK:
                type = 4;
                break;
            case LOG:
            case LOG_2:
                type = 5; // wood
                break;
            case GRAVEL:
                type = 6; // cement, close enough
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

    public Material toBukkitBlockType(int type) {
        Material material;
        // TODO: refactor reverse translation
        switch (type) {
            case 0: material = Material.AIR; break;
            case 1: material = Material.GRASS; break;
            case 2: material = Material.SAND; break;
            case 3: material = Material.STONE; break;
            case 4: material = Material.BRICK; break;
            case 5: material = Material.LOG; break;
            case 6: material = Material.GRAVEL; break;
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
