package xyz.chener.napt.server.core

import io.netty.channel.ChannelHandlerContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import xyz.chener.napt.common.entity.DataFrameCode
import xyz.chener.napt.common.entity.DataFrameEntity
import xyz.chener.napt.server.core.proxy.TcpPortProxy
import xyz.chener.napt.server.core.proxy.UdpPortProxy
import xyz.chener.napt.common.entity.ProxyType
import xyz.chener.napt.server.repository.ClientItemRepository


@Component
open class MessageHandle {

    @Autowired
    lateinit var clientItemRepository: ClientItemRepository

    @Autowired
    lateinit var clientManager: ClientManager

    @Autowired
    lateinit var trafficLimiter: TrafficLimiter


    fun access(ctx: ChannelHandlerContext, data: DataFrameEntity.DataFrame, isForce: Boolean){

        kotlin.runCatching {
            val clientItems = clientItemRepository.findClientItemByClientUid(data.clientUid)

            if (clientItems.isEmpty()) {
                ctx.channel().writeAndFlush(
                    DataFrameEntity.DataFrame.newBuilder()
                        .setCode(DataFrameCode.ACCESS_FAIL.code)
                        .setMessage(DataFrameCode.ACCESS_FAIL.message)
                        .build()
                )
                ctx.close()
                return
            }

            val channelId = clientManager.clientUidToClientChannelId[data.clientUid]

            if (channelId != null && !isForce) {
                ctx.channel().writeAndFlush(
                    DataFrameEntity.DataFrame.newBuilder()
                        .setCode(DataFrameCode.ACCESS_TOO_MANY.code)
                        .setMessage(DataFrameCode.ACCESS_TOO_MANY.message)
                        .build()
                )
                ctx.channel().close()
                return
            }

            if (channelId != null) {
                clientManager.kickClient(data.clientUid)
            }

            clientManager.authClient(ctx, data.clientUid, clientItems)
        }.onFailure {
            ctx.channel().writeAndFlush(
                DataFrameEntity.DataFrame.newBuilder()
                    .setCode(DataFrameCode.ACCESS_FAIL.code)
                    .setMessage("${DataFrameCode.ACCESS_FAIL.message} : ${it.message}")
                    .build()
            )
            ctx.channel().close()
        }

    }


    fun onTcpClientData(ctx : ChannelHandlerContext , data : DataFrameEntity.DataFrame ){
        val clientUID = findClientUidByClientBackendChannelId(ctx.channel().id().asLongText())

        if (clientUID == null){
            ctx.channel().writeAndFlush(
                DataFrameEntity.DataFrame.newBuilder()
                    .setCode(DataFrameCode.ACCESS_FAIL.code)
                    .setMessage("${DataFrameCode.ACCESS_FAIL.message} : 客户端未授权")
                    .build()
            )
            ctx.close()
            return
        }

        clientManager.portProxys[clientUID]?.forEach {
            if(it.proxyType == ProxyType.TCP && it.proxyType.code == data.requestNtType && it.clientAddress == data.clientAddress){
                val sendData = data.data.toByteArray()
                if (trafficLimiter.check(clientUID,it.clientAddress,it.port,sendData.size)  &&  it is TcpPortProxy){
                    it.sendToClient(data.tcpRemoteChannelId,sendData)
                }
            }
        }
    }


    fun onUdpClientData(ctx : ChannelHandlerContext , data : DataFrameEntity.DataFrame ){
        val clientUID = findClientUidByClientBackendChannelId(ctx.channel().id().asLongText())

        if (clientUID == null){
            ctx.channel().writeAndFlush(
                DataFrameEntity.DataFrame.newBuilder()
                    .setCode(DataFrameCode.ACCESS_FAIL.code)
                    .setMessage("${DataFrameCode.ACCESS_FAIL.message} : 客户端未授权")
                    .build()
            )
            ctx.close()
            return
        }

        clientManager.portProxys[clientUID]?.forEach {
            if(it.proxyType == ProxyType.UDP && it.proxyType.code == data.requestNtType && it.clientAddress == data.clientAddress){
                val sendData = data.data.toByteArray()
                if (trafficLimiter.check(clientUID,it.clientAddress,it.port,sendData?.size?:0)  &&  it is UdpPortProxy){
                    it.sendToClient(data.udpRemoteIp,data.udpRemotePort,sendData)
                }
            }

        }
    }


    fun onTcpClientCloseRemoteChannel(ctx : ChannelHandlerContext, data : DataFrameEntity.DataFrame ){
        val clientUID = findClientUidByClientBackendChannelId(ctx.channel().id().asLongText()) ?: return

        clientManager.portProxys[clientUID]?.forEach {
            if (it.proxyType == ProxyType.TCP && it.proxyType.code == data.requestNtType && it is TcpPortProxy && it.clientAddress == data.clientAddress){
                it.closeRemoteChannel(data.tcpRemoteChannelId)
            }
        }
    }


    fun onHeartBeatMessage(ctx: ChannelHandlerContext, data: DataFrameEntity.DataFrame) {
        ctx.channel().writeAndFlush(data)
    }


    fun onClientSyncMessage(ctx: ChannelHandlerContext, data: DataFrameEntity.DataFrame) {
        clientManager.getClientInfoLockCache[data.message]?.let {
            data.data.toByteArray()?.let { bts ->
                try {
                    it.lock.lock()
                    it.result = String(bts)
                    it.condition.signalAll()
                } finally {
                    it.lock.unlock()
                }
            }

        }
    }


    private fun findClientUidByClientBackendChannelId(clientBackendChannelId:String) : String?{
        clientManager.clientUidToClientChannelId.forEach { (clientUid, channelId) ->
            if (channelId == clientBackendChannelId){
                return@findClientUidByClientBackendChannelId clientUid
            }
        }
        return null
    }

}