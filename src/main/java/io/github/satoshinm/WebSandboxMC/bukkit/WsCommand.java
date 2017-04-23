package io.github.satoshinm.WebSandboxMC.bukkit;

import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import io.netty.channel.Channel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;

import java.net.InetSocketAddress;

public class WsCommand implements CommandExecutor {
    private WebSocketServerThread webSocketServerThread;

    public WsCommand(WebSocketServerThread webSocketServerThread) {
        this.webSocketServerThread = webSocketServerThread;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] split) {

        if (split.length == 0) {
            sender.sendMessage("/websandbox list -- list all web users connected");
            sender.sendMessage("/websandbox tp <user> -- teleport to given web username");
            sender.sendMessage("/websandbox kick <user> -- disconnect given web username");
            // TODO: reload, reconfig commands
            return true;
        }

        String subcommand = split[0];

        if (subcommand.equals("list")) {
            int size = webSocketServerThread.webPlayerBridge.name2channel.size();
            sender.sendMessage(size + " web player(s) connected:");

            int i = 1;
            for (String name: webSocketServerThread.webPlayerBridge.name2channel.keySet()) { // TODO: sort?
                Channel channel = webSocketServerThread.webPlayerBridge.name2channel.get(name);

                String ip = ((InetSocketAddress) channel.remoteAddress()).getHostString() +
                        ":" + ((InetSocketAddress) channel.remoteAddress()).getPort();

                Entity entity = webSocketServerThread.webPlayerBridge.channelId2Entity.get(channel);
                String entityInfo = "";

                if (entity != null) {
                    entityInfo += " entity "+entity.getEntityId()+" "+entity.getClass().getName();
                    entityInfo += " at " + entity.getLocation();
                }

                sender.sendMessage(i + ". " + name + ", " + ip + entityInfo);
                ++i;
            }
        }

        return true;
    }
}

