package xyz.chener.ext.napt.server.core;

/**
 * @Author: chenzp
 * @Date: 2023/05/22/17:23
 * @Email: chen@chener.xyz
 */

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import io.netty.channel.ChannelHandlerContext;
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
        List<RequestNt> requestNts = ConnectCache.portStarts.get(clientUid);

        List<ClientItem> clients = new LambdaQueryChainWrapper<>(StrongStarter.getMapper(ClientItemMapper.class))
                .eq(ClientItem::getClientUid, clientUid).list();

        // step1 requestNts有 clients 没有的  requestNts删除

        Iterator<RequestNt> it = requestNts.stream().filter(requestNt -> clients.stream().noneMatch(c -> c.getClientUid().equals(requestNt.getClientUid())
                && c.getServerPort().equals(requestNt.getPort())
                && c.getClientAddr().equals(requestNt.getClientAddr()))).iterator();

        while (it.hasNext()){
            RequestNt requestNt = it.next();
            requestNt.stop();
            it.remove();
        }

        // step2 clients有 requestNts 没有的  requestNts添加
        ArrayList<ClientItem> clientSub = new ArrayList<>(clients);
        clientSub.removeIf(ct -> requestNts.stream().anyMatch(requestNt -> requestNt.getClientAddr().equals(ct.getClientAddr()) && requestNt.getPort().equals(ct.getServerPort()) && requestNt.getClientUid().equals(ct.getClientUid())));
        clientSub.forEach(e->{
            requestNts.add(new RequestNt(e.getClientUid(),e.getServerPort(),e.getClientAddr(), e.getSpeedLimit().intValue()));
        });

        // step3 requestNts为空的话全部删除
        if (requestNts.size() < 1){
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
