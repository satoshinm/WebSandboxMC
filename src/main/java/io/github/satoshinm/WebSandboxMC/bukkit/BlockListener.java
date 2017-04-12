
package io.github.satoshinm.WebSandboxMC.bukkit;

import io.github.satoshinm.WebSandboxMC.bridge.BlockBridge;
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

    public BlockBridge blockBridge;

    public BlockListener(BlockBridge blockBridge) {
        this.blockBridge = blockBridge;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        blockBridge.notifyBlockUpdate(x, y, z, Material.AIR);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        Material material = block.getType();

        blockBridge.notifyBlockUpdate(x, y, z, material);
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
