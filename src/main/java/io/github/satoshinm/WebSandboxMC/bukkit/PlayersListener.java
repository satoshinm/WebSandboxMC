package io.github.satoshinm.WebSandboxMC.bukkit;

import io.github.satoshinm.WebSandboxMC.bridge.PlayersBridge;
import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayersListener implements Listener {

    private final PlayersBridge playersBridge;

    public PlayersListener(PlayersBridge playersBridge) {
        this.playersBridge = playersBridge;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String formattedMessage = event.getFormat().format(event.getMessage());
        formattedMessage = "<" + event.getPlayer().getDisplayName() + "> " + formattedMessage; // TODO: why doesn't getFormat() take care of this?

        playersBridge.notifyChat(formattedMessage);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();

        // TODO
    }
}
