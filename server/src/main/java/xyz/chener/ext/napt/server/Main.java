package xyz.chener.ext.napt.server;

import com.google.protobuf.ByteString;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import xyz.chener.ext.napt.server.core.*;
import xyz.chener.ext.napt.server.core.requestNt.RequestNt;
import xyz.chener.ext.napt.server.entity.DataFrameEntity;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class Main {



    public static void main(String[] args) throws  Exception {


        log.info("PID : [{}]", ManagementFactory.getRuntimeMXBean().getPid());

        Continer.put(ConfigLoader.class,new ConfigLoader());
        Continer.put(StrongStarter.class,new StrongStarter()).start();
        Continer.put(TrafficCounter.class,new TrafficCounter());
        Continer.put(ServerNettyMain.class,new ServerNettyMain()).start();

        Continer.put(ClientManager.class,new ClientManager());
        Continer.put(HttpServer.class,new HttpServer());

        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            stop("外部终止信号");
        }));
    }

    private static final AtomicBoolean isclosing = new AtomicBoolean(false);
    public static void stop(String reason){
        if (isclosing.compareAndSet(false,true)){
            log.info("服务关闭,原因: "+reason);
            Continer.get(StrongStarter.class).stop();
            Continer.get(ServerNettyMain.class).stop();
            Continer.get(HttpServer.class).stop();
        }
    }

}