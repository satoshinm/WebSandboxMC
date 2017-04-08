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

import java.util.List;
import java.util.Locale;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Echoes uppercase content of text frames.
 */
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    /* TODO: send initial server messages on client connect here, example:

U,1,0,0,0,0,0
E,1491627331.01,600
T,Welcome to Craft!
T,Type "/help" for a list of commands.
N,1,guest1

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("channel now active");
        ctx.channel().writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer("T,Welcome to WebSandboxMC\n".getBytes())));
    }
    */

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        // ping and pong frames already handled

        if (frame instanceof TextWebSocketFrame) {
            // TODO: remove text frames, not used
            // Send the uppercase string back.
            String request = ((TextWebSocketFrame) frame).text();
            System.out.println("channel " + ctx.channel() + " received request " + request);
            ctx.channel().writeAndFlush(new TextWebSocketFrame(request.toUpperCase(Locale.US)));
        } else if (frame instanceof BinaryWebSocketFrame) {
            ByteBuf content = ((BinaryWebSocketFrame) frame).content();

            byte[] bytes = new byte[content.capacity()];
            content.getBytes(0, bytes);

            String string = new String(bytes);
            System.out.println("received "+content.capacity()+" bytes: "+string);

            // TODO: handle more client messages

            if (string.startsWith("V,")) { // TODO: move to channel active, but it isn't sent there? want initial client connect
                String response = "T,Welcome to WebSandboxMC\n";

                List<World> worlds = Bukkit.getServer().getWorlds();
                response += "T,Worlds loaded: " + worlds.size() + "\n";
                response += "B,0,0,0,30,0,1\n"; // floating grass block at (0,30,0) in chunk (0,0)
                response += "K,0,0,0\n"; // update chunk key (0,0) to 0
                response += "R,0,0\n"; // refresh chunk (0,0)
                ctx.channel().writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(response.getBytes())));


                World world = worlds.get(0);

                // Send blocks around this area in the Bukkit world
                int x_center = -85;
                int y_center = 78;
                int z_center = 93;

                // of this dimension, +/-d
                int d = 16;

                // raised this amount in the web world
                int y_offset = 20;

                response = "";
                for (int i = -d; i < d; ++i) {
                    for (int j = -d; j < d; ++j) {
                        for (int k = -d; k < d; ++k) {
                            Block block = world.getBlockAt(i + x_center, j + y_center, k + z_center);
                            int type; // = block.getTypeId();
                            switch (block.getType()) {
                                case AIR:
                                    type = 0;
                                    break;
                                case GRASS:
                                    type = 1;
                                    break;
                                case SAND:
                                    type = 2;
                                    break;
                                    // TODO: ores, for now, showing as stone
                                case COAL_ORE:
                                case IRON_ORE:
                                //case DIAMOND_ORE:
                                //case EMERALD_ORE:
                                case REDSTONE_ORE:
                                case GLOWING_REDSTONE_ORE:
                                case LAPIS_ORE:
                                //case QUARTZ_ORE:
                                case GOLD_ORE:
                                case STONE:
                                    type = 3;
                                    break;
                                case BRICK:
                                    type = 4;
                                    break;
                                case LOG:
                                case LOG_2:
                                    type = 5; // wood
                                    break;
                                case GRAVEL:
                                    type = 6; // cement, close enough
                                case DIRT:
                                    type = 7;
                                    break;
                                case WOOD:
                                    type = 8; // plank
                                    break;
                                case SNOW:
                                case SNOW_BLOCK:;
                                    type = 9;
                                    break;
                                case GLASS:
                                    type = 10;
                                    break;
                                case COBBLESTONE:
                                    type = 11;
                                    break;
                                    // TODO: light stone (12)
                                    // TODO: dark stone (13)
                                case CHEST:
                                    type = 14;
                                    break;
                                case LEAVES:
                                case LEAVES_2:
                                    type = 15;
                                    break;
                                    // TODO: cloud (16)
                                case DOUBLE_PLANT: // TODO: other double plants, but a lot look like longer long grass
                                case LONG_GRASS:
                                    type = 17; // tall grass
                                    break;
                                case YELLOW_FLOWER:
                                    type = 18;
                                    break;
                                case RED_ROSE:
                                    type = 19;
                                    break;
                                case CHORUS_FLOWER:
                                    type = 20;
                                    break;
                                // TODO: sunflower (21)
                                // TODO: white flower (22)
                                // TODO: blue flower (23)

                                default:
                                    System.out.println("unknown block type="+block.getType());
                                    // unknown/unsupported becomes cloud
                                    // TODO: support more
                                    type = 16;

                            }

                            response = "B,0,0,"+(i+d)+","+(j+d+y_offset)+","+(k+d)+","+type+"\n";
                            ctx.channel().writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(response.getBytes())));
                        }
                    }
                }
                response = "K,0,0,1\n";
                ctx.channel().writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(response.getBytes())));

                response = "R,0,0\n";
                ctx.channel().writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(response.getBytes())));

                response = "T,Blocks sent\n";
                ctx.channel().writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(response.getBytes())));


                System.out.println("Sending response: "+response);

            }
        } else {
            String message = "unsupported frame type: " + frame.getClass().getName();
            throw new UnsupportedOperationException(message);
        }
    }
}
