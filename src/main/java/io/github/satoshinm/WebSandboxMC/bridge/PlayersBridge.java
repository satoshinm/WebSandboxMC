package io.github.satoshinm.WebSandboxMC.bridge;

import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class PlayersBridge {

    private final WebSocketServerThread webSocketServerThread;

    public PlayersBridge(WebSocketServerThread webSocketServerThread) {
        this.webSocketServerThread = webSocketServerThread;
    }

    public void sendPlayers(Channel channel) {
        for (Player player: Bukkit.getServer().getOnlinePlayers()) {
            int id = player.getEntityId();
            String name = player.getDisplayName();
            webSocketServerThread.sendLine(channel, "N," + id + "," + name);
        }
    }

    public void notifyMove(int id, Location location) {
        if (!webSocketServerThread.blockBridge.withinSandboxRange(location)) {
            // ignore movements outside of sandbox range TODO: maybe allow some grace outside of the block sandbox? +
            return;
        }

        double x = webSocketServerThread.blockBridge.toWebLocationEntityX(location);
        double y = webSocketServerThread.blockBridge.toWebLocationEntityY(location);
        double z = webSocketServerThread.blockBridge.toWebLocationEntityZ(location);

        // TODO: pitch and yaw
        int rx = 0;
        int ry = 0;

        webSocketServerThread.broadcastLine("P," + id + "," + x + "," + y + "," + z + "," + rx + "," + ry);
    }

    // TODO: player join/leave update entities

    public void notifyChat(String message) {
        webSocketServerThread.broadcastLine("T," + message);
    }

    public void clientChat(ChannelHandlerContext ctx, String theirName, String chat) {
        String formattedChat = "<" + theirName + "> " + chat;
        webSocketServerThread.broadcastLine("T," + formattedChat);
        Bukkit.getServer().broadcastMessage(formattedChat); // TODO: only to permission name?

        // TODO: support some server /commands?
    }
}
