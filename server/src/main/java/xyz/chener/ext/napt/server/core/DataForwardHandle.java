package xyz.chener.ext.napt.server.core;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import xyz.chener.ext.napt.server.core.requestNt.RequestNt;
import xyz.chener.ext.napt.server.core.requestNt.RequestNtTcp;
import xyz.chener.ext.napt.server.core.requestNt.RequestNtUdp;
import xyz.chener.ext.napt.server.entity.ClientItem;
import xyz.chener.ext.napt.server.entity.DataFrameCode;
import xyz.chener.ext.napt.server.entity.DataFrameEntity;
import xyz.chener.ext.napt.server.entity.RequestNtType;
import xyz.chener.ext.napt.server.mapper.ClientItemMapper;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 主要与客户端通信处理类
 */

@Slf4j
public class DataForwardHandle  extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) throws Exception {
        if (msg instanceof DataFrameEntity.DataFrame data){
            switch(data.getCode()){
                case DataFrameCode.ACCESS -> Handler.access(ctx,data,false);
                case DataFrameCode.ACCESS_FORCE -> Handler.access(ctx,data,true);
                case DataFrameCode.CLIENT_CHANNEL_ACCEPT_TCP -> Handler.onTcpClientData(ctx,data);
                case DataFrameCode.CLIENT_CLOSE_REMOTE_CHANNEL -> Handler.onClientCloseRemoteChannel(ctx,data);
                case DataFrameCode.CLIENT_CHANNEL_ACCEPT_UDP -> Handler.onUdpClientData(ctx,data);
                case DataFrameCode.GET_CLIENT_CONNECTS -> Handler.onGetClientConnects(ctx,data);
                case DataFrameCode.HEART_BEAT ->Handler.onHeartBeatMessage(ctx,data);
            }
        }else {
            ctx.channel().close();
        }
    }


    @Override
    public void channelInactive(@NotNull ChannelHandlerContext ctx) throws Exception {
        Handler.onClientClose(ctx.channel().id().asLongText());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("传输异常:{}",cause.getMessage());
        Handler.onClientClose(ctx.channel().id().asLongText());
        ctx.channel().close();
    }

    private static class Handler {
        public static void access(ChannelHandlerContext ctx, DataFrameEntity.DataFrame data,Boolean isForce)  {
            ClientItemMapper clientItemMapper = StrongStarter.getMapper(ClientItemMapper.class);
            List<ClientItem> clients = new LambdaQueryChainWrapper<>(clientItemMapper)
                    .eq(ClientItem::getClientUid, data.getClientUid())
                    .list();
            if (clients == null || clients.isEmpty()){
                ctx.channel().writeAndFlush(DataFrameEntity.DataFrame.newBuilder()
                        .setCode(DataFrameCode.ACCESS_FAIL)
                        .setMessage("客户端不被允许")
                        .build());
                ctx.close();
            }else {

                try {
                    if (ConnectCache.clientChannel.containsKey(data.getClientUid()) && !isForce){
                        ctx.channel().writeAndFlush(DataFrameEntity.DataFrame.newBuilder()
                                .setCode(DataFrameCode.ACCESS_TOO_MANY)
                                .setMessage("客户端已经连接,不可重复连接")
                                .build());
                        ctx.channel().close();
                        return;
                    }

                    if (isForce){
                        List<RequestNt> requestNts = ConnectCache.portStarts.remove(data.getClientUid());
                        if (requestNts != null){
                            try {
                                requestNts.forEach(RequestNt::stop);
                            }catch (Exception ignored){}
                        }

                        String cannalId = ConnectCache.clientChannel.remove(data.getClientUid());
                        try {
                            ChannelHandlerContext context = ConnectCache.channelMap.remove(cannalId);
                            if (context != null){
                                context.channel().writeAndFlush(DataFrameEntity.DataFrame.newBuilder()
                                        .setCode(DataFrameCode.CLOSE)
                                        .setMessage("其它客户端登录,强制断开连接"));
                                context.channel().close();
                            }
                        }catch (Exception ignored){ }
                    }

                    String json = new ObjectMapper().writeValueAsString(clients.stream().map(ClientItem::getClientAddr).toList());
                    ctx.channel().writeAndFlush(DataFrameEntity.DataFrame.newBuilder()
                            .setCode(DataFrameCode.ACCESS_SUCCESS)
                            .setMessage(json)
                            .build());

                    ConnectCache.clientChannel.put(data.getClientUid(),ctx.channel().id().asLongText());
                    ConnectCache.channelMap.put(ctx.channel().id().asLongText(),ctx);

                    List<RequestNt> ps = new CopyOnWriteArrayList<>();
                    clients.forEach(ec-> {
                        if (RequestNtType.TCP.getCode().equals(ec.getRequestNtType())){
                            ps.add(new RequestNtTcp(RequestNtType.TCP,ec.getClientUid(), ec.getServerPort(),
                                    ec.getClientAddr(),ec.getSpeedLimit().intValue()));
                        }

                        if (RequestNtType.UDP.getCode().equals(ec.getRequestNtType())){
                            ps.add(new RequestNtUdp(RequestNtType.UDP,ec.getClientUid(), ec.getServerPort(),
                                    ec.getClientAddr(),ec.getSpeedLimit().intValue()));
                        }
                    });
                    ConnectCache.portStarts.put(data.getClientUid(),ps);

                }catch (Exception exception){
                    ctx.channel().writeAndFlush(DataFrameEntity.DataFrame.newBuilder()
                            .setCode(DataFrameCode.ACCESS_FAIL)
                            .setMessage(exception.getMessage())
                            .build());
                    ctx.channel().close();
                }
            }
        }

        public static void onTcpClientData(ChannelHandlerContext ctx, DataFrameEntity.DataFrame data){
            String clientAddr = data.getClientAddress();
            String clientUID = findClientUidByChannelId(ctx.channel().id().asLongText());
            if (clientUID == null){
                ctx.channel().writeAndFlush(DataFrameEntity.DataFrame.newBuilder()
                        .setCode(DataFrameCode.ACCESS_FAIL)
                        .setMessage("客户端未授权")
                        .build());
                ctx.close();
                return;
            }

            List<RequestNt> requestNts = ConnectCache.portStarts.get(clientUID);
            requestNts.stream().filter(e-> RequestNtType.TCP.equals(e.getType())).forEach(e->{
                if(e.getClientAddr().equals(clientAddr)){
                    byte[] senddata = data.getData().toByteArray();
                    TrafficCounter trafficCounter = Continer.get(TrafficCounter.class);
                    if (trafficCounter != null){
                        if (!trafficCounter.add(clientUID,clientAddr,e.getPort(),Integer.valueOf(senddata.length).longValue())) {
                            trafficCounter.sendFlow(ctx,clientUID,e.getPort(),clientAddr);
                            return;
                        }
                    }
                    if (e instanceof RequestNtTcp rTcp){
                        rTcp.write(data.getTcpRemoteChannelId(),senddata);
                    }
                }
            });
        }


        public static void onUdpClientData(ChannelHandlerContext ctx, DataFrameEntity.DataFrame data){
            String clientAddr = data.getClientAddress();
            String clientUID = findClientUidByChannelId(ctx.channel().id().asLongText());
            if (clientUID == null){
                ctx.channel().writeAndFlush(DataFrameEntity.DataFrame.newBuilder()
                        .setCode(DataFrameCode.ACCESS_FAIL)
                        .setMessage("客户端未授权")
                        .build());
                ctx.close();
                return;
            }

            List<RequestNt> requestNts = ConnectCache.portStarts.get(clientUID);
            requestNts.stream().filter(e-> RequestNtType.UDP.equals(e.getType())).forEach(e->{
                if(e.getClientAddr().equals(clientAddr)){
                    byte[] senddata = data.getData().toByteArray();
                    TrafficCounter trafficCounter = Continer.get(TrafficCounter.class);
                    if (trafficCounter != null){
                        if (!trafficCounter.add(clientUID,clientAddr,e.getPort(),Integer.valueOf(senddata.length).longValue())) {
                            trafficCounter.sendFlow(ctx,clientUID,e.getPort(),clientAddr);
                            return;
                        }
                    }
                    if (e instanceof RequestNtUdp rUdp){
                        rUdp.write(data.getUdpRemoteIp(),data.getUdpRemotePort(),senddata);
                    }
                }
            });

        }

        public static void onClientClose(String clientChannelId){
            String clientUID = findClientUidByChannelId(clientChannelId);
            if (clientUID == null){
                return;
            }

            ConnectCache.clientChannel.remove(clientUID);
            ConnectCache.channelMap.remove(clientChannelId);
            List<RequestNt> requestNts = ConnectCache.portStarts.remove(clientUID);
            if (requestNts != null){
                try {
                    requestNts.forEach(RequestNt::stop);
                }catch (Exception ignored){}
            }
        }

        public static void onClientCloseRemoteChannel(ChannelHandlerContext ctx,DataFrameEntity.DataFrame data){
            String clientUID = findClientUidByChannelId(ctx.channel().id().asLongText());
            if (clientUID == null){
                return;
            }

            List<RequestNt> requestNts = ConnectCache.portStarts.get(clientUID)
                    .stream().filter(e-> RequestNtType.TCP.equals(e.getType())).toList();
            for (RequestNt nt : requestNts) {
                if (nt instanceof RequestNtTcp rTcp){
                    if (nt.getClientAddr().equals(data.getClientAddress())){
                        rTcp.closeOneChannel(data.getTcpRemoteChannelId());
                        break;
                    }
                }
            }
        }

        public static void onGetClientConnects(ChannelHandlerContext ctx,DataFrameEntity.DataFrame data){
            try {
                Map map = new ObjectMapper().readValue(data.getMessage(), Map.class);
                String code = map.get("code").toString();
                if (HttpServer.clientConnInfoCache.containsKey(code)) {
                    HttpServer.clientConnInfoCache.put(code,map.toString());
                }
            }catch (Exception ex){
                log.warn("client connect info parse error:{}",ex.getMessage());
            }
        }

        public static void onHeartBeatMessage(ChannelHandlerContext ctx,DataFrameEntity.DataFrame data){
            ctx.channel().writeAndFlush(data);
        }


        private static String findClientUidByChannelId(String channelId)
        {
            Iterator<String> it = ConnectCache.clientChannel.keys().asIterator();
            while (it.hasNext()){
                String key = it.next();
                if (ConnectCache.clientChannel.get(key).equals(channelId)){
                    return key;
                }
            }
            return null;
        }

    }

}
