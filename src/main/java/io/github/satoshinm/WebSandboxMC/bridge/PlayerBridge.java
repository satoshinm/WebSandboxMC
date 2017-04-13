package io.github.satoshinm.WebSandboxMC.bridge;

import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Sheep;

import java.util.HashMap;
import java.util.Map;

/**
 * Bridges the web client players
 */
public class PlayerBridge {

    private final WebSocketServerThread webSocketServerThread;

    private int lastPlayerID;
    public Map<ChannelId, String> channelId2name;
    private Map<String, ChannelId> name2channelId;
    private Map<ChannelId, Entity> channelId2Entity;

    public PlayerBridge(WebSocketServerThread webSocketServerThread) {
        this.webSocketServerThread = webSocketServerThread;

        this.lastPlayerID = 0;
        this.channelId2name = new HashMap<ChannelId, String>();
        this.name2channelId = new HashMap<String, ChannelId>();
        this.channelId2Entity = new HashMap<ChannelId, Entity>();
    }

    public String newPlayer(Channel channel) {
        int theirID = ++this.lastPlayerID;
        String theirName = "webguest" + theirID;

        this.channelId2name.put(channel.id(), theirName);
        this.name2channelId.put(theirName, channel.id());

        webSocketServerThread.sendLine(channel, "T,Welcome to WebSandboxMC, "+theirName+"!");

        // HumanEntity.class fails on Glowstone with https://gist.github.com/satoshinm/ebc87cdf1d782ba91b893fe24cd8ffd2
        // so use sheep instead for now
        //Class entityClass = HumanEntity.class;
        Class entityClass = Sheep.class;

        // Spawn an entity in the web user's place
        Location location = webSocketServerThread.blockBridge.spawnLocation;
        Entity entity = webSocketServerThread.blockBridge.world.spawn(location, entityClass);
        entity.setCustomName(theirName); // name tag
        entity.setCustomNameVisible(true); // TODO: shows error saving on Glowstone? https://gist.github.com/satoshinm/8a7cb999c09bb42048e70647875f53c8
        entity.setGravity(false); // allow flying TODO: this doesn't seem to work on Glowstone? drops like a rock
        this.channelId2Entity.put(channel.id(), entity);

        return theirName;
    }

    public void clientMoved(Channel channel, double x, double y, double z, double rx, double ry) {
        Entity entity = this.channelId2Entity.get(channel.id());

        Location location = this.webSocketServerThread.blockBridge.toBukkitPlayerLocation(x, y, z);

        // Move the surrogate entity to represent where the web player is
        entity.teleport(location);
    }
}
