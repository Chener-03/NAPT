package xyz.chener.ext.napt.client.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import xyz.chener.ext.napt.client.entity.DataFrameCode;
import xyz.chener.ext.napt.client.entity.DataFrameEntity;

import java.net.URL;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: chenzp
 * @Date: 2023/05/23/16:20
 * @Email: chen@chener.xyz
 */

@Slf4j
public class ClientDataHandle extends ChannelInboundHandlerAdapter {


    // 对应 远程 channelID 和 本地 RequestClient
    private final ConcurrentHashMap<String,RequestClient> remoteRequestMap = new ConcurrentHashMap<>();


    private final boolean forceAccess;

    private final String uid;

    public ClientDataHandle(boolean forceAccess,String uid) {
        this.forceAccess = forceAccess;
        this.uid = uid;
    }


    @Override
    public void channelActive(@NotNull ChannelHandlerContext ctx) throws Exception {
        DataFrameEntity.DataFrame data = DataFrameEntity.DataFrame.newBuilder()
                .setMessage(uid)
                .setCode(forceAccess? DataFrameCode.ACCESS_FORCE:DataFrameCode.ACCESS)
                .build();
        ctx.writeAndFlush(data);
        log.info("请求授权");
    }

    @Override
    public void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) throws Exception {
        if (msg instanceof DataFrameEntity.DataFrame data){
            switch (data.getCode()) {
                case DataFrameCode.ACCESS_FAIL, DataFrameCode.ACCESS_TOO_MANY ->{
                    log.error("授权失败:{}",data.getMessage());
                    Continer.get(ClientCore.class).stop();
                }

                case DataFrameCode.ACCESS_SUCCESS -> log.info("授权成功,映射的地址:{}",data.getMessage());

                case DataFrameCode.REMOTE_CHANNEL_ACCEPT -> {
                    String addr = data.getMessage();
                    String remoteChannelId = data.getRemoteChannelId();
                    RequestClient requestClient = remoteRequestMap.get(remoteChannelId);
                    if (requestClient == null){
                        String addrWithoutProtocol = addr;
                        if (!addr.startsWith("http://") && !addr.startsWith("https://")) {
                            addrWithoutProtocol = "http://" + addr;
                        }
                        URL url = new URL(addrWithoutProtocol);
                        requestClient = new RequestClient(addr,url.getHost(),url.getPort(),remoteChannelId,data.getData().toByteArray());
                        remoteRequestMap.put(remoteChannelId,requestClient);
                    }else {
                        requestClient.sendData(data.getData().toByteArray());
                    }
                }

                case DataFrameCode.REMOTE_CHANNEL_CLOSE -> {
                    String remoteChannelId = data.getRemoteChannelId();
                    RequestClient requestClient = remoteRequestMap.remove(remoteChannelId);
                    try {
                        requestClient.stop();
                    } catch (Exception ignored) { }
                }
            }
        }
    }

}
