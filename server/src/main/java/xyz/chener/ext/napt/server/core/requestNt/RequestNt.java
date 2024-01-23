package xyz.chener.ext.napt.server.core.requestNt;

import com.google.protobuf.ByteString;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import xyz.chener.ext.napt.server.core.ConnectCache;
import xyz.chener.ext.napt.server.core.Continer;
import xyz.chener.ext.napt.server.core.TrafficCounter;
import xyz.chener.ext.napt.server.entity.DataFrameCode;
import xyz.chener.ext.napt.server.entity.DataFrameEntity;
import xyz.chener.ext.napt.server.entity.RequestNtType;
import xyz.chener.ext.napt.server.utils.Utils;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class RequestNt {

    @Getter
    protected final RequestNtType type;

    protected static final NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
    protected static final NioEventLoopGroup workGroup = new NioEventLoopGroup(5);

    @Getter
    protected final String clientUid;

    @Getter
    protected final Integer port;

    @Getter
    protected final String clientAddr;

    protected Thread thread;

    protected Channel channel = null;

    protected volatile boolean isStart = true;

    protected final Lock lock = new ReentrantLock();


    // 进出流量限制
    protected int speedLimit;

    @Getter
    protected GlobalTrafficShapingHandler speedLimitHandler = null;

    protected RequestNt(RequestNtType type, String clientUid, Integer port, String clientAddr) {
        this.type = type;
        this.clientUid = clientUid;
        this.port = port;
        this.clientAddr = clientAddr;
    }




    public void stop()
    {
        lock.lock();
        try {
            isStart = false;
            if (Objects.nonNull(channel))
                channel.close();
            thread.interrupt();
            thread.join(2000);
        }catch (Exception ignored){}
        finally {
            lock.unlock();
        }
    }


}
