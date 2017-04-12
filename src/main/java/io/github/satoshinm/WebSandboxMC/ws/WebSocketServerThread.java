/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.github.satoshinm.WebSandboxMC.ws;

import io.github.satoshinm.WebSandboxMC.bridge.BlockBridge;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A HTTP server which serves Web Socket requests at:
 *
 * http://localhost:8080/websocket
 *
 * Open your browser at <a href="http://localhost:8080/">http://localhost:8080/</a>, then the demo page will be loaded
 * and a Web Socket connection will be made automatically.
 *
 * This server illustrates support for the different web socket specification versions and will work with:
 *
 * <ul>
 * <li>Safari 5+ (draft-ietf-hybi-thewebsocketprotocol-00)
 * <li>Chrome 6-13 (draft-ietf-hybi-thewebsocketprotocol-00)
 * <li>Chrome 14+ (draft-ietf-hybi-thewebsocketprotocol-10)
 * <li>Chrome 16+ (RFC 6455 aka draft-ietf-hybi-thewebsocketprotocol-17)
 * <li>Firefox 7+ (draft-ietf-hybi-thewebsocketprotocol-10)
 * <li>Firefox 11+ (RFC 6455 aka draft-ietf-hybi-thewebsocketprotocol-17)
 * </ul>
 */
public final class WebSocketServerThread extends Thread {

    private int PORT;
    private boolean SSL;

    private ChannelGroup allUsersGroup;
    private int lastPlayerID;
    private Map<ChannelId, String> channelId2name;
    private Map<String, ChannelId> name2channelId;

    private String ourExternalAddress;
    private int ourExternalPort;
    public BlockBridge blockBridge;

    public WebSocketServerThread(int port, String ourExternalAddress, int ourExternalPort) {
        this.PORT = port;
        this.SSL = false; // TODO: support ssl?

        this.blockBridge = null;

        this.allUsersGroup = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);
        this.lastPlayerID = 0;
        this.channelId2name = new HashMap<ChannelId, String>();
        this.name2channelId = new HashMap<String, ChannelId>();

        this.ourExternalAddress = ourExternalAddress;
        this.ourExternalPort = ourExternalPort;
    }

    @Override
    public void run() {
        try {
            // Configure SSL.
            final SslContext sslCtx;
            if (SSL) {
                SelfSignedCertificate ssc = new SelfSignedCertificate();
                sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
            } else {
                sslCtx = null;
            }

            EventLoopGroup bossGroup = new NioEventLoopGroup(1);
            EventLoopGroup workerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .handler(new LoggingHandler(LogLevel.INFO))
                        .childHandler(new WebSocketServerInitializer(sslCtx, this, ourExternalAddress, ourExternalPort));

                Channel ch = b.bind(PORT).sync().channel();

                System.out.println("Open your web browser and navigate to " +
                        (SSL ? "https" : "http") + "://127.0.0.1:" + PORT + "/index.html");

                ch.closeFuture().sync();
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }



    public void sendLine(Channel channel, String message) {
        channel.writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer((message + "\n").getBytes())));
    }

    public void broadcastLine(String message) {
        allUsersGroup.writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer((message + "\n").getBytes())));
    }

    // Handle a command from the client
    public void handleNewClient(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        int theirID = ++this.lastPlayerID;
        String theirName = "webguest" + theirID;
        allUsersGroup.add(channel);
        this.channelId2name.put(channel.id(), theirName);
        this.name2channelId.put(theirName, channel.id());

    /* Send initial server messages on client connect here, example from Python server for comparison:

U,1,0,0,0,0,0
E,1491627331.01,600
T,Welcome to Craft!
T,Type "/help" for a list of commands.
N,1,guest1
*/

        sendLine(channel,"T,Welcome to WebSandboxMC, "+theirName+"!");

        sendLine(channel, "B,0,0,0,30,0,1"); // floating grass block at (0,30,0) in chunk (0,0)
        sendLine(channel, "K,0,0,0"); // update chunk key (0,0) to 0
        sendLine(channel, "R,0,0"); // refresh chunk (0,0)

        blockBridge.sendWorld(channel);

        broadcastLine("T," + theirName + " has joined.");
    }
    // TODO: cleanup clients when they disconnect

    public void handle(String string, ChannelHandlerContext ctx) {
        if (string.startsWith("B,")) {
            System.out.println("client block update: "+string);
            String[] array = string.trim().split(",");
            if (array.length != 5) {
                throw new RuntimeException("malformed block update B, command from client: "+string);
            }
            int x = Integer.parseInt(array[1]);
            int y = Integer.parseInt(array[2]);
            int z = Integer.parseInt(array[3]);
            int type = Integer.parseInt(array[4]);

            blockBridge.clientBlockUpdate(ctx, x, y, z, type);
        } else if (string.startsWith("T,")) {
            String chat = string.substring(2).trim();
            String theirName = this.channelId2name.get(ctx.channel().id());
            String formattedChat = "<" + theirName + "> " + chat;
            broadcastLine("T," + formattedChat);
            Bukkit.getServer().broadcastMessage(formattedChat); // TODO: only to permission name?

            // TODO: support some server /commands?
        }
        // TODO: handle more client messages
    }

    public void notifyChat(String message) {
        broadcastLine("T," + message);
    }
}
