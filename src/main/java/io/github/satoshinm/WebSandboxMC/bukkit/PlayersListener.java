package io.github.satoshinm.WebSandboxMC.bukkit;

import io.github.satoshinm.WebSandboxMC.bridge.PlayersBridge;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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
        Player player = event.getPlayer();

        playersBridge.notifyMove(player.getEntityId(), player.getDisplayName(), event.getTo());
    }

    @EventHandler(ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        playersBridge.notifyAdd(player.getEntityId(), player.getDisplayName(), player.getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        playersBridge.notifyDelete(player.getEntityId());
    }
}
