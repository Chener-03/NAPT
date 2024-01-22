package xyz.chener.ext.napt.server.core;

/**
 * @Author: chenzp
 * @Date: 2023/05/22/17:23
 * @Email: chen@chener.xyz
 */

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import io.netty.channel.ChannelHandlerContext;
import xyz.chener.ext.napt.server.core.requestNt.RequestNtTcp;
import xyz.chener.ext.napt.server.entity.ClientItem;
import xyz.chener.ext.napt.server.entity.DataFrameCode;
import xyz.chener.ext.napt.server.entity.DataFrameEntity;
import xyz.chener.ext.napt.server.mapper.ClientItemMapper;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * 刷新 web 在线修改后的client连接
 */
public class ClientManager {

    private final Object lock = new Object();

    public void flush(String clientUid){
        if (!ConnectCache.clientChannel.containsKey(clientUid)) {
            return;
        }
        List<RequestNtTcp> requestNtTcps = ConnectCache.portStarts.get(clientUid);

        List<ClientItem> clients = new LambdaQueryChainWrapper<>(StrongStarter.getMapper(ClientItemMapper.class))
                .eq(ClientItem::getClientUid, clientUid).list();

        // step1 requestNts有 clients 没有的  requestNts删除

        Iterator<RequestNtTcp> it = requestNtTcps.stream().filter(requestNtTcp -> clients.stream().noneMatch(c -> c.getClientUid().equals(requestNtTcp.getClientUid())
                && c.getServerPort().equals(requestNtTcp.getPort())
                && c.getClientAddr().equals(requestNtTcp.getClientAddr()))).iterator();

        while (it.hasNext()){
            RequestNtTcp requestNtTcp = it.next();
            requestNtTcp.stop();
            it.remove();
        }

        // step2 clients有 requestNtTcps 没有的  requestNts添加
        ArrayList<ClientItem> clientSub = new ArrayList<>(clients);
        clientSub.removeIf(ct -> requestNtTcps.stream().anyMatch(requestNtTcp -> requestNtTcp.getClientAddr().equals(ct.getClientAddr()) && requestNtTcp.getPort().equals(ct.getServerPort()) && requestNtTcp.getClientUid().equals(ct.getClientUid())));
        clientSub.forEach(e->{
            requestNtTcps.add(new RequestNtTcp(e.getClientUid(),e.getServerPort(),e.getClientAddr(), e.getSpeedLimit().intValue()));
        });

        // step3 requestNts为空的话全部删除
        if (requestNtTcps.isEmpty()){
            ConnectCache.portStarts.remove(clientUid);
            String channelId = ConnectCache.clientChannel.remove(clientUid);
            ChannelHandlerContext ctx = ConnectCache.channelMap.remove(channelId);
            if (ctx != null){
                ctx.channel().writeAndFlush(DataFrameEntity.DataFrame.newBuilder()
                        .setCode(DataFrameCode.ACCESS_FAIL)
                        .setMessage("客户端ID未绑定任何节点")
                        .build());
                ctx.channel().close();
            }
        }
    }

    public boolean addClient (String clientUid,String clientAddr,int port,Long maxFlowLimit,Long speedLimit,String remark){
        synchronized (lock){
            ClientItemMapper clientItemMapper = StrongStarter.getMapper(ClientItemMapper.class);
            Long count = new LambdaQueryChainWrapper<>(clientItemMapper)
                    .eq(ClientItem::getClientUid, clientUid)
                    .eq(ClientItem::getClientAddr, clientAddr)
                    .eq(ClientItem::getServerPort, port)
                    .count();
            if (count > 0)
                return false;

            ClientItem ci = new ClientItem();
            ci.setClientUid(clientUid);
            ci.setClientAddr(clientAddr);
            ci.setServerPort(port);
            ci.setCreateTime(new Date());
            ci.setMaxFlowLimit(maxFlowLimit);
            ci.setSpeedLimit(speedLimit);
            ci.setRemark(remark);
            clientItemMapper.insert(ci);
            flush(clientUid);
            return true;
        }
    }



    public void removeClient (String clientUid,String clientAddr,int port){
        synchronized (lock){
            ClientItemMapper clientItemMapper = StrongStarter.getMapper(ClientItemMapper.class);
            new LambdaUpdateChainWrapper<>(clientItemMapper)
                    .eq(ClientItem::getClientUid, clientUid)
                    .eq(ClientItem::getClientAddr, clientAddr)
                    .eq(ClientItem::getServerPort, port).remove();
            flush(clientUid);
        }
    }



}
