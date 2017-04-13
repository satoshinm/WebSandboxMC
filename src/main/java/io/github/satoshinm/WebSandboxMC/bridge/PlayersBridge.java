package io.github.satoshinm.WebSandboxMC.bridge;

import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.Bukkit;
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