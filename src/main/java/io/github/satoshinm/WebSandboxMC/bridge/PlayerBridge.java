package io.github.satoshinm.WebSandboxMC.bridge;

import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;

import java.util.HashMap;
import java.util.Map;

/**
 * Bridges the web client players
 */
public class PlayerBridge {

    private final WebSocketServerThread webSocketServerThread;

    private int lastPlayerID;
    public Map<ChannelId, String> channelId2name;
    public Map<String, ChannelId> name2channelId;

    public PlayerBridge(WebSocketServerThread webSocketServerThread) {
        this.webSocketServerThread = webSocketServerThread;

        this.lastPlayerID = 0;
        this.channelId2name = new HashMap<ChannelId, String>();
        this.name2channelId = new HashMap<String, ChannelId>();
    }

    public String newPlayer(Channel channel) {
        int theirID = ++this.lastPlayerID;
        String theirName = "webguest" + theirID;

        this.channelId2name.put(channel.id(), theirName);
        this.name2channelId.put(theirName, channel.id());

        webSocketServerThread.sendLine(channel, "T,Welcome to WebSandboxMC, "+theirName+"!");

        return theirName;
    }
}
