package xyz.chener.ext.napt.server.core.requestNt;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import lombok.Getter;
import xyz.chener.ext.napt.server.entity.RequestNtType;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class RequestNt {


    protected final RequestNtType type;

    protected static final NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
    protected static final NioEventLoopGroup workGroup = new NioEventLoopGroup(5);

    @Getter
    protected final String clientUid;

    @Getter
    protected final Integer port;

    @Getter
    protected final String clientAddr;

    protected final Thread thread;

    protected Channel channel = null;

    protected volatile boolean isStart = true;

    protected final Lock lock = new ReentrantLock();

    // 存放当前端口连接通道   channelId -> channel
    @Getter
    protected final ConcurrentHashMap<String, ChannelHandlerContext> map = new ConcurrentHashMap<>();

    // 进出流量限制
    protected final int speedLimit;

    @Getter
    protected GlobalTrafficShapingHandler speedLimitHandler = null;

}
