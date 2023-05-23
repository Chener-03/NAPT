package xyz.chener.ext.napt.client.core;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import xyz.chener.ext.napt.client.entity.DataFrameCode;
import xyz.chener.ext.napt.client.entity.DataFrameEntity;

/**
 * @Author: chenzp
 * @Date: 2023/05/23/16:20
 * @Email: chen@chener.xyz
 */

@Slf4j
public class ClientDataHandle extends ChannelInboundHandlerAdapter {

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
                case DataFrameCode.ACCESS_FAIL ->{
                    log.error("授权失败:{}",data.getMessage());
                    ctx.channel().close();
                }
            }
        }
    }

}
