package xyz.chener.ext.napt.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import org.jetbrains.annotations.NotNull;
import xyz.chener.ext.napt.client.core.ClientCore;
import xyz.chener.ext.napt.client.core.ConfigLoader;
import xyz.chener.ext.napt.client.core.Continer;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args)  {

        Continer.put(ConfigLoader.class,new ConfigLoader());
        Continer.put(ClientCore.class,new ClientCore());
    }
}