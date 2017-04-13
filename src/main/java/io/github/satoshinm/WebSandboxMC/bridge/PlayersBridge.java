package io.github.satoshinm.WebSandboxMC.bridge;

import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Bridges other Bukkit player's positions/names/chats to the web clients
 */
public class PlayersBridge {

    private final WebSocketServerThread webSocketServerThread;

    private Set<Integer> playersInSandbox;

    public PlayersBridge(WebSocketServerThread webSocketServerThread) {
        this.webSocketServerThread = webSocketServerThread;

        this.playersInSandbox = new HashSet<Integer>();
    }

    public void sendPlayers(Channel channel) {
        for (Player player: Bukkit.getServer().getOnlinePlayers()) {
            int id = player.getEntityId();
            Location location = player.getLocation();
            String name = player.getDisplayName();

            if (this.playersInSandbox.contains(id)) {
                webSocketServerThread.sendLine(channel, "P," + id + "," + encodeLocation(location));
                webSocketServerThread.sendLine(channel, "N," + id + "," + name);
            }
        }

        // Web players
        for (Map.Entry<ChannelId, Entity> entry : webSocketServerThread.webPlayerBridge.channelId2Entity.entrySet()) {
            ChannelId channelId = entry.getKey();

            if (channelId.equals(channel.id())) {
                // No third person, web players don't need entities for themselves
                continue;
            }

            Entity entity = entry.getValue();

            int id = entity.getEntityId();
            Location location = entity.getLocation();
            String name = entity.getCustomName();

            webSocketServerThread.sendLine(channel, "P," + id + "," + encodeLocation(location));
            webSocketServerThread.sendLine(channel, "N," + id + "," + name);
        }
    }

    public String encodeLocation(Location location) {
        double x = webSocketServerThread.blockBridge.toWebLocationEntityX(location);
        double y = webSocketServerThread.blockBridge.toWebLocationEntityY(location);
        double z = webSocketServerThread.blockBridge.toWebLocationEntityZ(location);

        // yaw is degrees, 0(360)=+z, 180=-z, 90=-x, 270=+x
        float yaw = location.getYaw();

        // pitch is degrees, -90 (upward-facing, +y), or 0 (level), to 90 (downward facing, -y)
        float pitch = location.getPitch();

        // Craft uses radians, and flips it
        double rx = -yaw * Math.PI / 180;
        double ry = -pitch * Math.PI / 180;

        return x + "," + y + "," + z + "," + rx + "," + ry;
    }

    public void notifyMove(int id, String name, Location location) {
        if (!webSocketServerThread.blockBridge.withinSandboxRange(location)) {
            // No position updates for players outside of the sandbox, but if they were previously inside, kill them
            if (this.playersInSandbox.contains(id)) {
                this.notifyDelete(id);
            }
            return;
        }

        if (!this.playersInSandbox.contains(id)) {
            // Transitioned from outside to inside sandbox - allocate
            this.notifyAdd(id, name, location);
        }

        webSocketServerThread.broadcastLine("P," + id + "," + encodeLocation(location));
    }

    public void notifyAdd(int id, String name, Location initialLocation) {
        if (!webSocketServerThread.blockBridge.withinSandboxRange(initialLocation)) {
            return;
        }
        this.playersInSandbox.add(id);

        // Craft requires P (position update) before N (name), since it allocates the entity in P...
        // even though it is named in N (before that, default name 'player'+id). Therefore we must send P first.
        // TODO: change this behavior on client, allowing N to allocate? OTOH, the initial position is important...
        this.notifyMove(id, name, initialLocation);

        webSocketServerThread.broadcastLine("N," + id + "," + name);
    }

    public void notifyDelete(int id) {
        if (this.playersInSandbox.contains(id)) {
            this.playersInSandbox.remove(id);
            // delete this entity
            webSocketServerThread.broadcastLine("D," + id);
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
