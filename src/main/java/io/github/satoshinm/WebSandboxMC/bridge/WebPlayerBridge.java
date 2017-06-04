package io.github.satoshinm.WebSandboxMC.bridge;

import io.github.satoshinm.WebSandboxMC.Settings;
import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.entity.EntityDamageEvent;
import org.json.simple.JSONObject;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Collection;
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

    private Map<String, String> playerAuthKeys = new HashMap<String, String>();
    private boolean clickableLinks;
    private boolean clickableLinksTellraw;
    private String publicURL;

    private boolean allowAnonymous;
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

        this.clickableLinks = settings.clickableLinks;
        this.clickableLinksTellraw = settings.clickableLinksTellraw;
        this.publicURL = settings.publicURL;

        this.allowAnonymous = settings.allowAnonymous;
        this.lastPlayerID = 0;
        this.channelId2name = new HashMap<ChannelId, String>();
        this.channelId2Entity = new HashMap<ChannelId, Entity>();
        this.entityId2Username = new HashMap<Integer, String>();
        this.name2channel = new HashMap<String, Channel>();
    }

    public boolean newPlayer(final Channel channel, String proposedUsername, String token) {
        String theirName;
        boolean authenticated = false;
        if (validateClientAuthKey(proposedUsername, token)) {
            theirName = proposedUsername;
            authenticated = true;
            // TODO: more features when logging in as an authenticated user: move to their last spawn?
        } else {
            if (!proposedUsername.equals("")) { // blank = anonymous
                webSocketServerThread.sendLine(channel, "T,Failed to login as "+proposedUsername);
            }

            if (!allowAnonymous) {
                webSocketServerThread.sendLine(channel,"T,This server requires authentication.");
                return false;
            }

            int theirID = ++this.lastPlayerID;
            theirName = "webguest" + theirID;
        }
        String ip = ((InetSocketAddress) channel.remoteAddress()).getHostString() +
                ":" + ((InetSocketAddress) channel.remoteAddress()).getPort();
        webSocketServerThread.log(Level.INFO, "New web client joined: " + theirName +
                (authenticated ? " (authenticated)" : " (anonymous)") + " from " + ip);

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
                entity.setGravity(false); // allow flying
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
        return true;
    }

    public void clearStaleEntities(CommandSender sender) {
        if (this.entityClass == null) {
            sender.sendMessage("Nothing to clear, no entity class set");
            return;
        }

        int removed = 0;
        int found = 0;

        // Get all entities within the web radius - note: server implementations may restrict these bounds
        double r = webSocketServerThread.blockBridge.radius;
        Collection<Entity> entities = webSocketServerThread.blockBridge.world.getNearbyEntities(
                webSocketServerThread.blockBridge.spawnLocation, r, r, r);
        for (Entity entity : entities) {
            ++found;

            webSocketServerThread.log(Level.INFO, "looking at entity "+entity);
            if (this.entityClass.isInstance(entity)) {

                if (entityId2Username.containsKey(entity.getEntityId())) {
                    webSocketServerThread.log(Level.INFO, "ignored, in use: "+entity+
                            " by "+entityId2Username.get(entity.getEntityId()));
                    continue;
                }

                // Skip entities that don't match our expected properties

                if (this.setCustomNames && entity.getCustomName() == null) {
                    webSocketServerThread.log(Level.INFO, "ignored, missing custom name: "+entity);
                    continue;
                }

                if (this.disableGravity && entity.hasGravity()) {
                    webSocketServerThread.log(Level.INFO, "ignored, missing no gravity: "+entity);
                    continue;
                }

                /* Glowstone seems to not persist setAI()?
                if (entity instanceof LivingEntity) {
                    LivingEntity livingEntity = (LivingEntity) entity;
                    if (this.disableAI && livingEntity.hasAI()) {
                        webSocketServerThread.log(Level.WARNING, "ignored, missing no AI: "+entity);
                        continue;
                    }
                }
                */

                webSocketServerThread.log(Level.INFO, "removing "+entity);

                sender.sendMessage("Removing " + entity);
                entity.remove();
                ++removed;
            }
        }
        sender.sendMessage("Removed "+removed+" of "+found+" entities");
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

    public void newClientAuthKey(String username, CommandSender sender) {
        String token = new BigInteger(130, random).toString(32);

        playerAuthKeys.put(username, token);
        // TODO: persist to disk


        String url = publicURL + "#++" + username + "+" + token;

        if (clickableLinks && sender instanceof Player) {
            Player player = (Player) sender;

            String linkText = "Click here to login";
            String hoverText = "Login to the web sandbox as " + player.getName();

            // There are two strategies since TextComponents fails with on Glowstone with an error:
            // java.lang.UnsupportedOperationException: Not supported yet.
            // at org.bukkit.entity.Player$Spigot.sendMessage(Player.java:1734)
            // see https://github.com/GlowstoneMC/Glowkit-Legacy/pull/8
            if (clickableLinksTellraw) {
                JSONObject json = new JSONObject();
                json.put("text", linkText);
                json.put("bold", true);

                JSONObject clickEventJson = new JSONObject();
                clickEventJson.put("action", "open_url");
                clickEventJson.put("value", url);
                json.put("clickEvent", clickEventJson);

                JSONObject hoverEventJson = new JSONObject();
                hoverEventJson.put("action", "show_text");
                JSONObject hoverTextObject = new JSONObject();
                hoverTextObject.put("text", hoverText);
                hoverEventJson.put("value", hoverTextObject);
                json.put("hoverEvent", hoverEventJson);

                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " " + json.toJSONString());
            } else {
                TextComponent message = new TextComponent(linkText);
                message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
                message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[] { new TextComponent(hoverText) }));
                message.setBold(true);

                player.spigot().sendMessage(message);
            }
        } else {
            sender.sendMessage("Visit this URL to login: " + url);
        }
    }

    private boolean validateClientAuthKey(String username, String token) {
        String expected = playerAuthKeys.get(username);
        if (expected == null) return false;
        return expected.equals(token);
        // TODO: load from disk
    }
}
