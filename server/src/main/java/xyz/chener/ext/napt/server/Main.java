package xyz.chener.ext.napt.server;

import com.google.protobuf.ByteString;
import xyz.chener.ext.napt.server.core.*;
import xyz.chener.ext.napt.server.entity.DataFrameEntity;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    public static void main(String[] args) {

        Continer.put(ConfigLoader.class,new ConfigLoader());
        Continer.put(StrongStarter.class,new StrongStarter()).start();
        Continer.put(TrafficCounter.class,new TrafficCounter());
        Continer.put(ServerNettyMain.class,new ServerNettyMain()).start();

        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            stop("外部终止信号");
        }));
    }

    private static final AtomicBoolean isclosing = new AtomicBoolean(false);
    public static void stop(String reason){
        if (isclosing.compareAndSet(false,true)){
            System.out.println("server is closing, reason: "+reason);
            Continer.get(StrongStarter.class).stop();
            Continer.get(ServerNettyMain.class).stop();
        }
    }

}