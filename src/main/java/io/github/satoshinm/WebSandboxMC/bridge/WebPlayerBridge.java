package io.github.satoshinm.WebSandboxMC.bridge;

import io.github.satoshinm.WebSandboxMC.Settings;
import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Sheep;
import org.bukkit.event.entity.EntityDamageEvent;

import java.math.BigInteger;
import java.security.SecureRandom;
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

    public Map<String, String> playerAuthKeys = new HashMap<String, String>();

    private boolean setCustomNames;
    private boolean disableGravity;
    private boolean disableAI;
    private Class<?> entityClass;
    private boolean constrainToSandbox;
    private boolean dieDisconnect;

    public WebPlayerBridge(WebSocketServerThread webSocketServerThread, Settings settings) {
        this.webSocketServerThread = webSocketServerThread;
        this.setCustomNames = settings.setCustomNames;
        this.disableGravity = settings.disableGravity;
        this.disableAI = settings.disableAI;

        if (settings.entityClassName == null || "".equals(settings.entityClassName)) {
            this.entityClass = null;
        } else {
            try {
                this.entityClass = Class.forName("org.bukkit.entity." + settings.entityClassName);
            } catch (ClassNotFoundException ex) {
                ex.printStackTrace();

                // HumanEntity.class fails on Glowstone with https://gist.github.com/satoshinm/ebc87cdf1d782ba91b893fe24cd8ffd2
                // so use sheep instead for now. TODO: spawn ala GlowNPC: https://github.com/satoshinm/WebSandboxMC/issues/13
                webSocketServerThread.log(Level.WARNING, "No such entity class " + settings.entityClassName + ", falling back to Sheep");
                this.entityClass = Sheep.class;
            }
        }

        this.constrainToSandbox = settings.entityMoveSandbox;
        this.dieDisconnect = settings.entityDieDisconnect;

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
            Entity entity = webSocketServerThread.blockBridge.world.spawn(location, (Class) this.entityClass);
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

            channelId2Entity.remove(channel.id());
            entityId2Username.remove(entity.getEntityId());

            entity.remove();
        }

        name2channel.remove(name);
    }

    public void deleteAllEntities() {
        for (Entity entity: channelId2Entity.values()) {
            entity.remove();
        }
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

    public void notifySheared(String username, String playerName) {
        Channel channel = name2channel.get(username);

        webSocketServerThread.log(Level.INFO, "web user " + username + " sheared by " + playerName);

        if (channel != null) {
            webSocketServerThread.sendLine(channel, "T,You were sheared by " + playerName);
        }
    }

    private final SecureRandom random = new SecureRandom();

    public String newClientAuthKey(String username) {
        String token = new BigInteger(130, random).toString(32);

        playerAuthKeys.put(username, token);

        return token;
        // TODO: persist to disk
    }

    private boolean validateClientAuthKey(String username, String token) {
        String expected = playerAuthKeys.get(username);
        if (expected == null) return false;
        return expected.equals(token);
        // TODO: load from disk
    }

    public void authenticateUser(ChannelHandlerContext ctx, String username, String token) {
        if (validateClientAuthKey(username, token)) {
            // Rename user from the default guest name
            ChannelId id = ctx.channel().id();
            this.channelId2name.put(id, username);
            Entity entity = this.channelId2Entity.get(id);
            if (entity != null) {
                this.entityId2Username.put(entity.getEntityId(), username);
            }
            this.name2channel.remove(username);
            this.name2channel.put(username, ctx.channel());
            // TODO: update entity custom name if has one

            webSocketServerThread.sendLine(ctx.channel(), "T,Successfully logged in as "+username);
        } else {
            webSocketServerThread.sendLine(ctx.channel(), "T,Invalid token, failed to login as "+username);
        }
        // TODO: anonymous auth
        // TODO: show "logged in" message here not earlier, since now know username!
    }
}
