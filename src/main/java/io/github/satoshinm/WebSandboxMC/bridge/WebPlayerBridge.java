package io.github.satoshinm.WebSandboxMC.bridge;

import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
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

    private boolean setCustomNames;
    private boolean disableGravity;

    public WebPlayerBridge(WebSocketServerThread webSocketServerThread, boolean setCustomNames, boolean disableGravity) {
        this.webSocketServerThread = webSocketServerThread;
        this.setCustomNames = setCustomNames;
        this.disableGravity = disableGravity;

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
        // TODO: configurable Bukkit entity types
        // TODO: allow disabling spawning Bukkit entity, but if disabled, would still have to track id for web entity
        //Class entityClass = HumanEntity.class;
        Class entityClass = Sheep.class;

        // Spawn an entity in the web user's place
        Location location = webSocketServerThread.blockBridge.spawnLocation;
        Entity entity = webSocketServerThread.blockBridge.world.spawn(location, entityClass);
        if (setCustomNames) {
            entity.setCustomName(theirName); // name tag
            entity.setCustomNameVisible(true);
        }
        if (disableGravity) {
            entity.setGravity(false); // allow flying TODO: this doesn't seem to work on Glowstone? drops like a rock
        }
        channelId2Entity.put(channel.id(), entity);

        // Notify other web clients (except this one) of this new user
        webSocketServerThread.broadcastLineExcept(channel.id(), "P,"+entity.getEntityId()+","+webSocketServerThread.playersBridge.encodeLocation(location));
        webSocketServerThread.broadcastLineExcept(channel.id(), "N,"+entity.getEntityId()+","+theirName);

        // TODO: should this go to Bukkit chat, too/instead? make configurable?
        webSocketServerThread.broadcastLine("T," + theirName + " has joined.");

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

    public void clientDisconnected(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        String name = webSocketServerThread.webPlayerBridge.channelId2name.get(channel.id());

        if (name == null) {
            // TODO: Why are some channels activated and inactivated without fully logging in? Either way, ignore.
            return;
        }

        webSocketServerThread.webPlayerBridge.channelId2name.remove(channel.id());

        System.out.println("web client disconnected: " + name);
        // TODO: should this go to Bukkit chat, too/instead? make configurable?
        webSocketServerThread.broadcastLine("T," + name + " has disconnected.");

        Entity entity = webSocketServerThread.webPlayerBridge.channelId2Entity.get(channel.id());
        if (entity != null) {
            webSocketServerThread.broadcastLineExcept(channel.id(), "D,"+entity.getEntityId());

            webSocketServerThread.webPlayerBridge.channelId2Entity.remove(entity);

            //((LivingEntity) entity).setHealth(0); // this kills the entity, but leaves drops (undesirable)
            // This removes the entity server-side, but it still shows on the client and attacking shows
            // "tried to attack an entity that does not exist" -- on Glowstone. Relogging clears. TODO: Glowstone bug
            entity.remove();
            //System.out.println("entity isDead? "+entity.isDead()+", isValid? "+entity.isValid());
        }
    }
}
