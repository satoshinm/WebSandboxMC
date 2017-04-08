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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;

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
    private int x_center, y_center, z_center, radius, y_offset;

    public WebSocketServerThread(int port, int x_center, int y_center, int z_center, int radius, int y_offset) {
        this.PORT = port;
        this.SSL = false; // TODO: support ssl?

        this.x_center = x_center;
        this.y_center = y_center;
        this.z_center = z_center;

        this.radius = radius;

        this.y_offset = y_offset;
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
                        .childHandler(new WebSocketServerInitializer(sslCtx, this));

                Channel ch = b.bind(PORT).sync().channel();

                System.out.println("Open your web browser and navigate to " +
                        (SSL ? "https" : "http") + "://127.0.0.1:" + PORT + '/');

                ch.closeFuture().sync();
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private int toWebBlockType(Material material) {
        int type;
        switch (material) {
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
                System.out.println("unknown block type="+material);
                // unknown/unsupported becomes cloud
                // TODO: support more
                type = 16;

        }
        return type;
    }

    private Material toBukkitBlockType(int type) {
        Material material;
        // TODO: refactor reverse translation
        switch (type) {
            case 0: material = Material.AIR; break;
            case 1: material = Material.GRASS; break;
            case 2: material = Material.SAND; break;
            case 3: material = Material.STONE; break;
            case 4: material = Material.BRICK; break;
            case 5: material = Material.LOG; break;
            case 6: material = Material.GRAVEL; break;
            case 7: material = Material.DIRT; break;
            case 8: material = Material.WOOD; break;
            case 9: material = Material.SNOW_BLOCK; break;
            case 10: material = Material.GLASS; break;
            case 11: material = Material.COBBLESTONE; break;
            //case 12: material = Material. light stone?
            //case 13: material = Material. dark stone?
            case 14: material = Material.CHEST; break;
            case 15: material = Material.LEAVES; break;
            //case 16: material = Material.clouds; break; // clouds
            case 17: material = Material.LONG_GRASS; break;
            case 18: material = Material.YELLOW_FLOWER; break;
            case 19: material = Material.RED_ROSE; break;
            case 20: material = Material.CHORUS_FLOWER; break;
            case 21: material = Material.DOUBLE_PLANT; break; // sunflower
            //case 22: material = Material.white flower
            //case 23: material = Material.blue flower
            default: material = Material.DIAMOND_ORE; // placeholder TODO fix
        }
        return material;
    }

    // Handle a command from the client
    public void handleNewClient(ChannelHandlerContext ctx) {
        String response = "T,Welcome to WebSandboxMC\n";

        List<World> worlds = Bukkit.getServer().getWorlds();
        response += "T,Worlds loaded: " + worlds.size() + "\n";
        response += "B,0,0,0,30,0,1\n"; // floating grass block at (0,30,0) in chunk (0,0)
        response += "K,0,0,0\n"; // update chunk key (0,0) to 0
        response += "R,0,0\n"; // refresh chunk (0,0)
        ctx.channel().writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(response.getBytes())));

        // TODO: configurable world
        World world = worlds.get(0);
        
        for (int i = -radius; i < radius; ++i) {
            for (int j = -radius; j < radius; ++j) {
                for (int k = -radius; k < radius; ++k) {
                    Block block = world.getBlockAt(i + x_center, j + y_center, k + z_center);
                    int type = toWebBlockType(block.getType());

                    response = "B,0,0," + (i + radius) + "," + (j + radius + y_offset) + "," + (k + radius) + "," + type + "\n";
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

        // Move player on top of the new blocks
        int x_start = radius;
        int y_start = world.getHighestBlockYAt(x_center, y_center) + 1 - radius - y_offset;
        int z_start = radius;
        int rotation_x = 0;
        int rotation_y = 0;
        response = "U,1," + x_start + "," + y_start + "," + z_start + "," + rotation_x + "," + rotation_y + "\n";
        ctx.channel().writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(response.getBytes())));

        System.out.println("Sending response: " + response);
    }

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

            Material material = toBukkitBlockType(type);
            x += -radius + x_center;
            y += -radius + y_center - y_offset;
            z += -radius + z_center;
            Block block = Bukkit.getServer().getWorlds().get(0).getBlockAt(x, y, z);
            if (block != null) {
                System.out.println("setting block ("+x+","+y+","+z+",) to "+material);
                block.setType(material);
            } else {
                System.out.println("no such block at "+x+","+y+","+z);
            }
        }
        // TODO: handle more client messages
    }

    public void notifyBlockUpdate(int x, int y, int z, Material material) {
        // TODO: send to all web clients within range, if within range, "B," command setting block to 0
        int type = toWebBlockType(material);


    }
}
