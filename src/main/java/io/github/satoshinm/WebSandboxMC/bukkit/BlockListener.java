
package io.github.satoshinm.WebSandboxMC.bukkit;

import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockListener implements Listener {

    private final WebSocketServerThread webSocketServerThread;

    public BlockListener(WebSocketServerThread webSocketServerThread) {
        this.webSocketServerThread = webSocketServerThread;
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

        webSocketServerThread.notifyBlockUpdate(x, y, z, block.getType());
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
}
