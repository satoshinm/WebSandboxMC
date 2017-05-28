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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.util.CharsetUtil;

import java.io.*;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Outputs index page content.
 */
public class WebSocketIndexPageHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final File pluginDataFolder;

    public WebSocketIndexPageHandler(File pluginDataFolder) {
        this.pluginDataFolder = pluginDataFolder;
    }

    private InputStream getResourceAsStream(String name) {
        // If it exists, use files in plugin resource directory - otherwise, embedded resources in our plugin jar

        // TODO: cache to avoid checking each time?
        File file = new File(this.pluginDataFolder, name);
        if (file.exists()) {
            try {
                return new FileInputStream(file);
            } catch (FileNotFoundException ex) {
                // fallthrough
            }
        }

        return getClass().getResourceAsStream(name);
    }

    private void sendTextResource(String prepend, String name, String mimeType, FullHttpRequest req, ChannelHandlerContext ctx) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader((this.getResourceAsStream(name))));
        // TODO: read only once and buffer
        String line;
        StringBuffer buffer = new StringBuffer();
        if (prepend != null) buffer.append(prepend);
        while ((line = reader.readLine()) != null) {
            buffer.append(line);
            buffer.append('\n');
        }
        ByteBuf content = Unpooled.copiedBuffer(buffer, java.nio.charset.Charset.forName("UTF-8"));

        FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, OK, content);

        res.headers().set(HttpHeaderNames.CONTENT_TYPE, mimeType);
        HttpUtil.setContentLength(res, content.readableBytes());

        sendHttpResponse(ctx, req, res);
    }

    private void sendBinaryResource(String name, String mimeType, FullHttpRequest req, ChannelHandlerContext ctx) throws IOException {
        DataInputStream stream = new DataInputStream(this.getResourceAsStream(name));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[16384];

        while ((nRead = stream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();

        ByteBuf content = Unpooled.copiedBuffer(buffer.toByteArray());

        FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, OK, content);

        res.headers().set(HttpHeaderNames.CONTENT_TYPE, mimeType);
        HttpUtil.setContentLength(res, content.readableBytes());

        sendHttpResponse(ctx, req, res);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        // Handle a bad request.
        if (!req.decoderResult().isSuccess()) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
        }

        // Allow only GET methods.
        if (req.method() != GET) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }

        // Send the index page
        if ("/".equals(req.uri()) || "/index.html".equals(req.uri()) || "/craft.html".equals(req.uri())) {
            sendTextResource(null,"/craft.html", "text/html; charset=UTF-8", req, ctx);
        } else if ("/craft.js".equals(req.uri()) || "/craftw.js".equals(req.uri())) {
            String prepend = "window.DEFAULT_ARGV = ['-'];"; // connect back to self
            sendTextResource(prepend, req.uri(), "application/javascript; charset=UTF-8", req, ctx);
        } else if ("/craft.html.mem".equals(req.uri())) {
            sendBinaryResource(req.uri(), "application/octet-stream", req, ctx);
        } else if ("/craftw.wasm".equals(req.uri())) {
            // craftw = webassembly build
            sendBinaryResource(req.uri(), "application/octet-stream", req, ctx);
        } else if ("/craft.data".equals(req.uri()) || "/craftw.data".equals(req.uri())) {
            // same data file for both asmjs and webassembly
            sendBinaryResource("/craft.data", "application/octet-stream", req, ctx);
        } else if ("/textures.zip".equals(req.uri())) {
            File file = new File(this.pluginDataFolder, "textures.zip");
            if (file.exists()) {
                sendBinaryResource(req.uri(), "application/octet-stream", req, ctx);
            } else {
                System.out.println("request for /textures.zip but does not exist in plugin data folder");
                sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, PRECONDITION_FAILED));
            }
        } else {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
        // Generate an error page if response getStatus code is not OK (200).
        if (res.status().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
            HttpUtil.setContentLength(res, res.content().readableBytes());
        }

        // Send the response and close the connection if necessary.
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!HttpUtil.isKeepAlive(req) || res.status().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }
}
