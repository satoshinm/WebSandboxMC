
package io.github.satoshinm.WebSandboxMC.bukkit;

import io.github.satoshinm.WebSandboxMC.bridge.BlockBridge;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockListener implements Listener {

    public BlockBridge blockBridge;

    public BlockListener(BlockBridge blockBridge) {
        this.blockBridge = blockBridge;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();
        blockBridge.notifyBlockUpdate(location, Material.AIR, (byte) 0);
    }

    @EventHandler(ignoreCancelled = true)
    @SuppressWarnings("deprecation") // Block#getData
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();

        blockBridge.notifyBlockUpdate(block.getLocation(), block.getType(), block.getData());
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
