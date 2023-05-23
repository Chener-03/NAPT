package xyz.chener.ext.napt.server.core;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.Assert;
import xyz.chener.ext.napt.server.entity.ClientItem;
import xyz.chener.ext.napt.server.entity.DataFrameCode;
import xyz.chener.ext.napt.server.entity.DataFrameEntity;
import xyz.chener.ext.napt.server.mapper.ClientItemMapper;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * 主要与客户端通信处理类
 */
public class DataForwardHandle  extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) throws Exception {
        if (msg instanceof DataFrameEntity.DataFrame data){
            switch(data.getCode()){
                case DataFrameCode.ACCESS -> Handler.access(ctx,data,false);
                case DataFrameCode.ACCESS_FORCE -> Handler.access(ctx,data,true);
                case DataFrameCode.CLIENT_CHANNEL_ACCEPT -> Handler.onClientData(ctx,data);
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
        Handler.onClientClose(ctx.channel().id().asLongText());
    }

    private static class Handler {
        public static void access(ChannelHandlerContext ctx, DataFrameEntity.DataFrame data,Boolean isForce)  {
            ClientItemMapper clientItemMapper = StrongStarter.getMapper(ClientItemMapper.class);
            List<ClientItem> clients = new LambdaQueryChainWrapper<>(clientItemMapper)
                    .eq(ClientItem::getClientUid, data.getMessage())
                    .list();
            if (clients == null || clients.size() == 0){
                ctx.channel().writeAndFlush(DataFrameEntity.DataFrame.newBuilder()
                        .setCode(DataFrameCode.ACCESS_FAIL)
                        .setMessage("客户端不被允许")
                        .build());
                ctx.close();
            }else {

                try {
                    if (ConnectCache.clientChannel.containsKey(data.getMessage()) && !isForce){
                        ctx.channel().writeAndFlush(DataFrameEntity.DataFrame.newBuilder()
                                .setCode(DataFrameCode.ACCESS_TOO_MANY)
                                .setMessage("客户端已经连接,不可重复连接")
                                .build());
                        return;
                    }

                    if (isForce){
                        List<RequestNt> requestNts = ConnectCache.portStarts.remove(data.getMessage());
                        if (requestNts != null){
                            try {
                                requestNts.forEach(RequestNt::stop);
                            }catch (Exception ignored){}
                        }

                        String cannalId = ConnectCache.clientChannel.remove(data.getMessage());
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

                    String json = new ObjectMapper().writeValueAsString(clients.stream().map(ClientItem::getClientAddr));
                    ctx.channel().writeAndFlush(DataFrameEntity.DataFrame.newBuilder()
                            .setCode(DataFrameCode.ACCESS_SUCCESS)
                            .setMessage(json)
                            .build());

                    ConnectCache.clientChannel.put(data.getMessage(),ctx.channel().id().asLongText());
                    ConnectCache.channelMap.put(ctx.channel().id().asLongText(),ctx);

                    List<RequestNt> ps = new CopyOnWriteArrayList<>();
                    clients.forEach(ec-> ps.add(new RequestNt(ec.getClientUid(), ec.getServerPort(), ec.getClientAddr(),ec.getSpeedLimit().intValue())));
                    ConnectCache.portStarts.put(data.getMessage(),ps);

                }catch (Exception exception){
                    ctx.channel().writeAndFlush(DataFrameEntity.DataFrame.newBuilder()
                            .setCode(DataFrameCode.ACCESS_FAIL)
                            .setMessage(exception.getMessage())
                            .build());
                }
            }
        }

        public static void onClientData(ChannelHandlerContext ctx,DataFrameEntity.DataFrame data){
            String clientAddr = data.getMessage();
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
            requestNts.forEach(e->{
                if(e.getClientAddr().equals(clientAddr)){
                    byte[] senddata = data.getData().toByteArray();
                    TrafficCounter trafficCounter = Continer.get(TrafficCounter.class);
                    if (trafficCounter != null){
                        if (!trafficCounter.add(clientUID,clientAddr,e.getPort(),Integer.valueOf(senddata.length).longValue())) {
                            trafficCounter.sendFlow(ctx,clientUID,e.getPort(),clientAddr);
                            return;
                        }
                    }
                    e.write(data.getRemoteChannelId(),senddata);
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
