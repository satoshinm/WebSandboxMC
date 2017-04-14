package io.github.satoshinm.WebSandboxMC.bridge;

import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Sheep;

import java.util.HashMap;
import java.util.Map;

/**
 * Bridges the web client players
 */
public class WebPlayerBridge {

    private final WebSocketServerThread webSocketServerThread;

    private int lastPlayerID;
    public Map<ChannelId, String> channelId2name;
    private Map<String, ChannelId> name2channelId;
    public Map<ChannelId, Entity> channelId2Entity;

    public WebPlayerBridge(WebSocketServerThread webSocketServerThread) {
        this.webSocketServerThread = webSocketServerThread;

        this.lastPlayerID = 0;
        this.channelId2name = new HashMap<ChannelId, String>();
        this.name2channelId = new HashMap<String, ChannelId>();
        this.channelId2Entity = new HashMap<ChannelId, Entity>();
    }

    public String newPlayer(final Channel channel) {
        int theirID = ++this.lastPlayerID;
        final String theirName = "webguest" + theirID;

        this.channelId2name.put(channel.id(), theirName);
        this.name2channelId.put(theirName, channel.id());

        webSocketServerThread.sendLine(channel, "T,Welcome to WebSandboxMC, "+theirName+"!");

        // HumanEntity.class fails on Glowstone with https://gist.github.com/satoshinm/ebc87cdf1d782ba91b893fe24cd8ffd2
        // so use sheep instead for now. TODO: spawn ala GlowNPC: https://github.com/satoshinm/WebSandboxMC/issues/13
        //Class entityClass = HumanEntity.class;
        Class entityClass = Sheep.class;

        // Spawn an entity in the web user's place
        Location location = webSocketServerThread.blockBridge.spawnLocation;
        Entity entity = webSocketServerThread.blockBridge.world.spawn(location, entityClass);
        entity.setCustomName(theirName); // name tag
        entity.setCustomNameVisible(true);
        entity.setGravity(false); // allow flying TODO: this doesn't seem to work on Glowstone? drops like a rock
        channelId2Entity.put(channel.id(), entity);

        // Notify other web clients (except this one) of this new user
        webSocketServerThread.broadcastLineExcept(channel.id(), "P,"+entity.getEntityId()+","+webSocketServerThread.playersBridge.encodeLocation(location));
        webSocketServerThread.broadcastLineExcept(channel.id(), "N,"+entity.getEntityId()+","+theirName);


        return theirName;
    }

    public void clientMoved(final Channel channel, final double x, final double y, final double z, final double rx, final double ry) {
        final Entity entity = this.channelId2Entity.get(channel.id());

        Location location = webSocketServerThread.blockBridge.toBukkitPlayerLocation(x, y, z);

        // Opposite of PlayerBridge encodeLocation - given negated radians, convert to degrees
        location.setYaw((float)(-rx * 180 / Math.PI));
        location.setPitch((float)(-ry * 180 / Math.PI));

        // Move the surrogate entity to represent where the web player is
        entity.teleport(location);

        // Notify other web clients (except this one) they moved
        webSocketServerThread.broadcastLineExcept(channel.id(), "P,"+entity.getEntityId()+","+webSocketServerThread.playersBridge.encodeLocation(location));
    }
}
