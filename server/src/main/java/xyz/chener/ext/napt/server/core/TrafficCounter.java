package xyz.chener.ext.napt.server.core;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import xyz.chener.ext.napt.server.entity.ClientItem;
import xyz.chener.ext.napt.server.entity.DataFrameCode;
import xyz.chener.ext.napt.server.entity.DataFrameEntity;
import xyz.chener.ext.napt.server.mapper.ClientItemMapper;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: chenzp
 * @Date: 2023/05/22/16:33
 * @Email: chen@chener.xyz
 */


@Slf4j
public class TrafficCounter {


    public boolean add(String clientUid,String address,int port , Long traffic){
        ClientItemMapper clientItemMapper = StrongStarter.getMapper(ClientItemMapper.class);
        ClientItem client = new LambdaQueryChainWrapper<>(clientItemMapper)
                .eq(ClientItem::getClientUid, clientUid)
                .eq(ClientItem::getClientAddr, address)
                .eq(ClientItem::getServerPort,port)
                .one();
        if (client == null){
            return false;
        }

        if (client.getMaxFlowLimit() == -1){
            return true;
        }

        if (client.getFlow() > client.getMaxFlowLimit()){
            return false;
        }

        try{
            clientItemMapper.add(clientUid,address,port,traffic);
        }catch (Exception exception){
            log.error("TrafficCounter add error",exception);
            return true;
        }
        return true;
    }

    public void sendFlow(ChannelHandlerContext ctx,String clientUid,int port,String address){
        ctx.channel().writeAndFlush(DataFrameEntity.DataFrame.newBuilder()
                .setCode(DataFrameCode.CLIENT_FLOW_LIMIT)
                .setMessage(String.format("客户端节点流量超限 : %s,%s,%s",clientUid,address,port))
                .build());
    }

}
