package io.github.satoshinm.WebSandboxMC.bridge;

import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Sheep;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Bridges the web client players
 */
public class WebPlayerBridge {

    private final WebSocketServerThread webSocketServerThread;

    private int lastPlayerID;
    public Map<ChannelId, String> channelId2name;
    public Map<ChannelId, Entity> channelId2Entity;
    public Map<Integer, String> entityId2Username;
    public Map<String, Channel> name2channel;

    private boolean setCustomNames;
    private boolean disableGravity;
    private boolean disableAI;
    private Class entityClass;
    private boolean constrainToSandbox;
    private boolean dieDisconnect;

    public WebPlayerBridge(WebSocketServerThread webSocketServerThread, boolean setCustomNames,
                           boolean disableGravity, boolean disableAI,
                           String entityClassName, boolean constrainToSandbox,
                           boolean dieDisconnect) {
        this.webSocketServerThread = webSocketServerThread;
        this.setCustomNames = setCustomNames;
        this.disableGravity = disableGravity;
        this.disableAI = disableAI;

        if (entityClassName == null || "".equals(entityClassName)) {
            this.entityClass = null;
        } else {
            try {
                this.entityClass = Class.forName("org.bukkit.entity." + entityClassName);
            } catch (ClassNotFoundException ex) {
                ex.printStackTrace();

                // HumanEntity.class fails on Glowstone with https://gist.github.com/satoshinm/ebc87cdf1d782ba91b893fe24cd8ffd2
                // so use sheep instead for now. TODO: spawn ala GlowNPC: https://github.com/satoshinm/WebSandboxMC/issues/13
                webSocketServerThread.log(Level.WARNING, "No such entity class " + entityClassName + ", falling back to Sheep");
                this.entityClass = Sheep.class;
            }
        }

        this.constrainToSandbox = constrainToSandbox;
        this.dieDisconnect = dieDisconnect;

        this.lastPlayerID = 0;
        this.channelId2name = new HashMap<ChannelId, String>();
        this.channelId2Entity = new HashMap<ChannelId, Entity>();
        this.entityId2Username = new HashMap<Integer, String>();
        this.name2channel = new HashMap<String, Channel>();
    }

    public String newPlayer(final Channel channel) {
        int theirID = ++this.lastPlayerID;
        final String theirName = "webguest" + theirID;

        this.channelId2name.put(channel.id(), theirName);
        this.name2channel.put(theirName, channel);

        webSocketServerThread.sendLine(channel, "T,Welcome to WebSandboxMC, "+theirName+"!");

        if (this.entityClass != null) {
            // Spawn an entity in the web user's place
            Location location = webSocketServerThread.blockBridge.spawnLocation;
            Entity entity = webSocketServerThread.blockBridge.world.spawn(location, this.entityClass);
            if (setCustomNames) {
                entity.setCustomName(theirName); // name tag
                entity.setCustomNameVisible(true);
            }
            if (disableGravity) {
                entity.setGravity(false); // allow flying TODO: this doesn't seem to work on Glowstone? drops like a rock. update: known bug: https://github.com/GlowstoneMC/Glowstone/issues/454
            }
            if (disableAI) {
                if (entity instanceof LivingEntity) {
                    LivingEntity livingEntity = (LivingEntity) entity;
                    livingEntity.setAI(false);
                }
            }
            channelId2Entity.put(channel.id(), entity);
            entityId2Username.put(entity.getEntityId(), theirName);

            // Notify other web clients (except this one) of this new user
            webSocketServerThread.broadcastLineExcept(channel.id(), "P," + entity.getEntityId() + "," + webSocketServerThread.playersBridge.encodeLocation(location));
            webSocketServerThread.broadcastLineExcept(channel.id(), "N," + entity.getEntityId() + "," + theirName);
        }

        // TODO: should this go to Bukkit chat, too/instead? make configurable?
        webSocketServerThread.broadcastLine("T," + theirName + " has joined.");

        return theirName;
    }

    public void clientMoved(final Channel channel, final double x, final double y, final double z, final double rx, final double ry) {
        if (this.entityClass == null) {
            // No bukkit entity, no web-bsaed entity for other players either TODO: synthesize a placeholder entity id for web-to-web only?
            return;
        }

        final Entity entity = this.channelId2Entity.get(channel.id());

        Location location = webSocketServerThread.blockBridge.toBukkitPlayerLocation(x, y, z);

        if (constrainToSandbox && !webSocketServerThread.blockBridge.withinSandboxRange(location)) {
            webSocketServerThread.log(Level.FINEST, "client tried to move outside of sandbox: "+location);
            return;
        }

        // Opposite of PlayerBridge encodeLocation - given negated radians, convert to degrees
        location.setYaw((float)(-rx * 180 / Math.PI));
        location.setPitch((float)(-ry * 180 / Math.PI));

        // Move the surrogate entity to represent where the web player is
        entity.teleport(location);

        // Notify other web clients (except this one) they moved
        webSocketServerThread.broadcastLineExcept(channel.id(), "P,"+entity.getEntityId()+","+webSocketServerThread.playersBridge.encodeLocation(location));
    }

    public void clientDisconnected(Channel channel) {
        String name = webSocketServerThread.webPlayerBridge.channelId2name.get(channel.id());

        if (name == null) {
            // TODO: Why are some channels activated and inactivated without fully logging in? Either way, ignore.
            return;
        }

        channelId2name.remove(channel.id());

        webSocketServerThread.log(Level.FINEST, "web client disconnected: " + name);
        // TODO: should this go to Bukkit chat, too/instead? make configurable?
        webSocketServerThread.broadcastLine("T," + name + " has disconnected.");

        Entity entity = channelId2Entity.get(channel.id());
        if (entity != null) {
            webSocketServerThread.broadcastLineExcept(channel.id(), "D,"+entity.getEntityId());

            channelId2Entity.remove(entity);

            entity.remove();
        }

        name2channel.remove(name);
    }

    public void notifyDied(String username, EntityDamageEvent.DamageCause cause) {
        webSocketServerThread.log(Level.INFO, "web user "+username+"'s entity died from "+cause);

        Channel channel = name2channel.get(username);
        if (channel != null) {
            String message = "T,You died from "+(cause == null ? "unknown causes" : cause);

            if (!dieDisconnect) {
                message += ", but remain connected to the server as a ghost";
            }

            webSocketServerThread.sendLine(channel, message);

            if (dieDisconnect) {
                channel.close();
                clientDisconnected(channel);
            }
        }
    }
}
