package io.github.satoshinm.WebSandboxMC.bukkit;

import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import io.netty.channel.Channel;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerShearEntityEvent;

import java.net.InetSocketAddress;

public class WsCommand implements CommandExecutor {
    private WebSocketServerThread webSocketServerThread;

    public WsCommand(WebSocketServerThread webSocketServerThread) {
        this.webSocketServerThread = webSocketServerThread;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] split) {
        String subcommand = split.length == 0 ? "help" : split[0];

        if (subcommand.equals("list")) {
            int size = webSocketServerThread.webPlayerBridge.name2channel.size();
            sender.sendMessage(size + " web player(s) connected:");

            boolean verbose = split.length >= 2 && split[1].equals("verbose");

            int i = 1;
            for (String name: webSocketServerThread.webPlayerBridge.name2channel.keySet()) { // TODO: sort?
                Channel channel = webSocketServerThread.webPlayerBridge.name2channel.get(name);

                String ip = ((InetSocketAddress) channel.remoteAddress()).getHostString() +
                        ":" + ((InetSocketAddress) channel.remoteAddress()).getPort();

                Entity entity = webSocketServerThread.webPlayerBridge.channelId2Entity.get(channel.id());
                String entityInfo = "";

                if (entity != null) {
                    entityInfo += " entity "+entity.getEntityId();
                    if (verbose) {
                        entityInfo += " "+entity.getClass().getName() + " at " + entity.getLocation();
                    }
                }

                sender.sendMessage(i + ". " + name + ", " + ip + entityInfo);
                ++i;
            }
            return true;
        } else if (subcommand.equals("tp")) {
            if (split.length < 2) {
                sender.sendMessage("Usage: /websandbox tp <user>");
                return true;
            }
            String name = split[1];

            Channel channel = webSocketServerThread.webPlayerBridge.name2channel.get(name);
            if (channel == null) {
                sender.sendMessage("No such web user: " + name);
                return true;
            }

            Entity entity = webSocketServerThread.webPlayerBridge.channelId2Entity.get(channel.id());
            if (entity == null) {
                sender.sendMessage("Web user "+name+" is connected, but has no spawned entity.");
                // TODO: allow tracking and teleporting independently of the Bukkit entity?
                return true;
            }

            Location location = entity.getLocation();
            if (!(sender instanceof Player)) {
                sender.sendMessage("Web user "+name+"'s entity is located at: "+location);
                sender.sendMessage("Web user "+name+"'s entity is "+entity);
                return true;
            }
            Player player = (Player) sender;
            player.sendMessage("Teleporting you to "+name+" at "+location+" for "+entity);
            player.teleport(entity);

            webSocketServerThread.sendLine(channel, "T,"+player.getDisplayName()+" teleported to you");
        } else if (subcommand.equals("kick")) {
            if (split.length < 2) {
                sender.sendMessage("Usage: /websandbox kick <user>");
                return true;
            }
            String name = split[1];

            Channel channel = webSocketServerThread.webPlayerBridge.name2channel.get(name);
            if (channel == null) {
                sender.sendMessage("No such web user: " + name);
                return true;
            }

            sender.sendMessage("Kicking web client "+name);
            webSocketServerThread.sendLine(channel,"T,You were kicked by "+sender.getName());
            webSocketServerThread.webPlayerBridge.clientDisconnected(channel);
            return true;
        } else { // help
            sender.sendMessage("/websandbox list [verbose] -- list all web users connected");
            sender.sendMessage("/websandbox tp <user> -- teleport to given web username");
            sender.sendMessage("/websandbox kick <user> -- disconnect given web username");
            // TODO: reload, reconfig commands
        }
        return false;
    }
}

