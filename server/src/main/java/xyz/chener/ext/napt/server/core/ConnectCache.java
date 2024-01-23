package xyz.chener.ext.napt.server.core;

import io.netty.channel.ChannelHandlerContext;
import xyz.chener.ext.napt.server.core.requestNt.RequestNt;
import xyz.chener.ext.napt.server.core.requestNt.RequestNtTcp;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectCache {

    // channelId -> channel
    public static final ConcurrentHashMap<String, ChannelHandlerContext> channelMap = new ConcurrentHashMap<>();
    // clientUid -> channelId
    public static final ConcurrentHashMap<String,String> clientChannel = new ConcurrentHashMap<>();

    // clientUid -> RequestNtTcp  存储每个客户端的转发服务的服务端port列表
    public static final ConcurrentHashMap<String, List<RequestNt>> portStarts = new ConcurrentHashMap<>();

}
