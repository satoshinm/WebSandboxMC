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

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.bukkit.Bukkit;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.service.ban.BanService;
import org.spongepowered.api.util.ban.Ban;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final WebSocketServerThread webSocketServerThread;
    private final Set<String> ipBans;
    private boolean checkIPBans;

    public WebSocketFrameHandler(WebSocketServerThread webSocketServerThread, boolean checkIPBans) {
        this.webSocketServerThread = webSocketServerThread;
        this.checkIPBans = checkIPBans;

        Set<String> ipBans = null;
        if (this.checkIPBans) {
            // TODO: refactor out Bukkit/Sponge banning into separate modules
            try {
                ipBans = Bukkit.getServer().getIPBans();
            } catch (NoClassDefFoundError e1) {
                try {
                    ipBans = new HashSet<String>();
                    BanService service = Sponge.getServiceManager().provide(BanService.class).get();
                    for (Ban.Ip b : service.getIpBans()) {
                        ipBans.add(b.getAddress().toString());
                    }
                } catch (NoClassDefFoundError e) {
                    throw new UnsupportedOperationException("Unable to get ban list, try setting checkIPBans: false");
                }
            }
        }
        this.ipBans = ipBans;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        webSocketServerThread.log(Level.FINEST, "channel read, frame="+frame);
        // TODO: log at INFO level if this the first data we received from a client (new first connection), to
        // help detect clients connecting but not sending authentication commands (in newPlayer)

        if (this.checkIPBans) {
            String ip = webSocketServerThread.getRemoteIP(ctx.channel());
            if (this.ipBans.contains(ip)) {
                webSocketServerThread.sendLine(ctx.channel(), "T,Banned from server"); // TODO: show reason, getBanList
                return;
            }
        }

        if (frame instanceof BinaryWebSocketFrame) {
            ByteBuf content = frame.content();

            byte[] bytes = new byte[content.capacity()];
            content.getBytes(0, bytes);

            final String string = new String(bytes);
            webSocketServerThread.log(Level.FINEST, "received "+content.capacity()+" bytes: "+string);

            this.webSocketServerThread.scheduleSyncTask(new Runnable() {
                @Override
                public void run() {
                    webSocketServerThread.handle(string, ctx);
                }
            });
        } else {
            String message = "unsupported frame type: " + frame.getClass().getName();
            throw new UnsupportedOperationException(message);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        webSocketServerThread.scheduleSyncTask(new Runnable() {
            @Override
            public void run() {
                webSocketServerThread.webPlayerBridge.clientDisconnected(ctx.channel());
            }
        });
    }
}
