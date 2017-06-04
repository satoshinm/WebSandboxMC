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

import io.github.satoshinm.WebSandboxMC.Settings;
import io.github.satoshinm.WebSandboxMC.bridge.BlockBridge;
import io.github.satoshinm.WebSandboxMC.bridge.WebPlayerBridge;
import io.github.satoshinm.WebSandboxMC.bridge.PlayersBridge;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
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

import java.net.InetSocketAddress;
import java.util.logging.Level;

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

    public BlockBridge blockBridge;
    public PlayersBridge playersBridge;
    public WebPlayerBridge webPlayerBridge;
    private Settings settings;

    public WebSocketServerThread(Settings settings) {
        this.PORT = settings.httpPort;
        this.SSL = false; // TODO: support ssl?

        this.blockBridge = null;
        this.playersBridge = null;
        this.webPlayerBridge = null;

        this.allUsersGroup = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);

        this.settings = settings;
    }

    public void log(Level level, String message) {
        settings.log(level, message);
    }

    public void scheduleSyncTask(Runnable runnable) {
        settings.scheduleSyncTask(runnable);
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
                        .handler(settings.nettyLogInfo ? new LoggingHandler(LogLevel.INFO) : new LoggingHandler())
                        .childHandler(new WebSocketServerInitializer(sslCtx, this,
                                settings.pluginDataFolder, settings.checkIPBans));

                Channel ch = b.bind(PORT).sync().channel();

                log(Level.INFO, "Open your web browser and navigate to " +
                        (SSL ? "https" : "http") + "://127.0.0.1:" + PORT + "/" +
                        " or " + settings.publicURL);

                ch.closeFuture().sync();
            } catch (InterruptedException ex) {
                // plugin is shutting down - let it interrupt quietly
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

    public void sendBinary(Channel channel, ByteBuf data) {
        channel.writeAndFlush(new BinaryWebSocketFrame(data));
    }

    public void broadcastLine(String message) {
        allUsersGroup.writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer((message + "\n").getBytes())));
    }

    public void broadcastLineExcept(ChannelId excludeChannelId, String message) {
        for (Channel channel: allUsersGroup) {
            if (channel.id().equals(excludeChannelId)) {
                continue;
            }

            channel.writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer((message + "\n").getBytes())));
        }
    }

    public void handleNewClient(ChannelHandlerContext ctx, String username, String token) {
        Channel channel = ctx.channel();

        if (!webPlayerBridge.newPlayer(channel, username, token)) {
            channel.close();
            return;
        }

        allUsersGroup.add(channel);


    /* Send initial server messages on client connect here, example from Python server for comparison:

U,1,0,0,0,0,0
E,1491627331.01,600
T,Welcome to Craft!
T,Type "/help" for a list of commands.
N,1,guest1
*/
        sendLine(channel, "B,0,0,0,30,0,1"); // floating grass block at (0,30,0) in chunk (0,0)
        sendLine(channel, "K,0,0,0"); // update chunk key (0,0) to 0
        sendLine(channel, "R,0,0"); // refresh chunk (0,0)

        blockBridge.sendWorld(channel);
        playersBridge.sendPlayers(channel);
    }

    public String getRemoteIP(Channel channel) {
        return ((InetSocketAddress) channel.remoteAddress()).getHostString();
        // TODO: respect X-Forwarded-For optionally, https://github.com/satoshinm/WebSandboxMC/issues/87
    }

    public int getRemotePort(Channel channel) {
        return ((InetSocketAddress) channel.remoteAddress()).getPort();
    }

    public String getRemoteIPandPort(Channel channel) {
        return getRemoteIP(channel) + ":" + getRemotePort(channel);
    }

    // Handle a command from the client
    public void handle(String string, ChannelHandlerContext ctx) {
        if (string.startsWith("A,")) {
            String[] array = string.trim().split(",");
            String username = "";
            String token = "";
            if (array.length == 3) {
                username = array[1];
                token = array[2];
            }
            handleNewClient(ctx, username, token);
            return;
        }

        if (!allUsersGroup.contains(ctx.channel())) {
            // Commands below this point require a successfully logged-in user
            this.log(Level.FINEST, "Client tried to send command when not authenticated: "+string+" from "+ctx);
            return;
        }

        if (string.startsWith("B,")) {
            this.log(Level.FINEST, "client block update: "+string);
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
            String theirName = this.webPlayerBridge.channelId2name.get(ctx.channel().id());

            playersBridge.clientChat(ctx, theirName, chat);
        } else if (string.startsWith("P,")) {
            String[] array = string.trim().split(",");
            if (array.length != 6) {
                throw new RuntimeException("malformed client position update P: "+string);
            }
            double x = Double.parseDouble(array[1]);
            double y = Double.parseDouble(array[2]);
            double z = Double.parseDouble(array[3]);
            double rx = Double.parseDouble(array[4]);
            double ry = Double.parseDouble(array[5]);

            webPlayerBridge.clientMoved(ctx.channel(), x, y, z, rx, ry);
        } else if (string.startsWith("S,")) {
            String[] array = string.trim().split(",", 6);
            if (array.length != 6) {
                throw new RuntimeException("malformed sign text update S: "+string);
            }

            int x = Integer.parseInt(array[1]);
            int y = Integer.parseInt(array[2]);
            int z = Integer.parseInt(array[3]);
            int face = Integer.parseInt(array[4]);
            String text = array[5];

            this.log(Level.FINEST, "new sign: "+x+","+y+","+z+" face="+face+", text="+text);

            this.blockBridge.clientNewSign(ctx, x, y, z, face, text);
        }

        // TODO: handle more client messages
    }
}
