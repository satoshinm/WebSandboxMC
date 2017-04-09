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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.ssl.SslContext;

/**
 */
public class WebSocketServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final String WEBSOCKET_PATH = "/";

    private final SslContext sslCtx;
    private final WebSocketServerThread webSocketServerThread;
    private final String ourExternalAddress;
    private final int ourExternalPort;

    public WebSocketServerInitializer(SslContext sslCtx, WebSocketServerThread webSocketServerThread, String ourExternalAddress, int ourExternalPort) {
        this.sslCtx = sslCtx;
        this.webSocketServerThread = webSocketServerThread;

        this.ourExternalAddress = ourExternalAddress;
        this.ourExternalPort = ourExternalPort;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        if (sslCtx != null) {
            pipeline.addLast(sslCtx.newHandler(ch.alloc()));
        }
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(65536));
        pipeline.addLast(new WebSocketServerCompressionHandler());
        pipeline.addLast(new WebSocketServerProtocolHandler(WEBSOCKET_PATH, null, true));
        // TODO: overload with '/' for html and ws? currently, /index.html is html, but / is ws (html attempt = 'not a WebSocket handshake request: missing upgrade')
        pipeline.addLast(new WebSocketIndexPageHandler(ourExternalAddress, ourExternalPort));
        pipeline.addLast(new WebSocketFrameHandler(webSocketServerThread));
    }
}
