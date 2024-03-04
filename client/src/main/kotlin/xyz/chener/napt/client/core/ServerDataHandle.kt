package xyz.chener.napt.client.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.ByteString
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import xyz.chener.napt.common.entity.DataFrameCode
import xyz.chener.napt.common.entity.DataFrameEntity
import java.net.URI


class ServerDataHandle(
    private val clientUid:String
    , private val isForce:Boolean
    , private val tcpRemoteRequestMap: MutableMap<String, TcpRequestClient>
    , private val udpRemoteRequestList: MutableList<UdpRequestClient>
) : ChannelInboundHandlerAdapter() {

    private val log: Logger = LoggerFactory.getLogger(ServerDataHandle::class.java)


    override fun channelActive(ctx: ChannelHandlerContext) {
        val data: DataFrameEntity.DataFrame = DataFrameEntity.DataFrame.newBuilder()
            .setClientUid(clientUid)
            .setCode(if (isForce) DataFrameCode.ACCESS_FORCE.code else DataFrameCode.ACCESS.code)
            .build()
        ctx.channel().writeAndFlush(data)
        log.info("请求授权 '${clientUid}' ... ")
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
        closeAllConnect()
    }
    override fun channelInactive(ctx: ChannelHandlerContext) {
        closeAllConnect()
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is DataFrameEntity.DataFrame) {
            when(msg.code){
                DataFrameCode.ACCESS_FAIL.code, DataFrameCode.ACCESS_TOO_MANY.code ->{
                    log.error("授权失败:{}", msg.getMessage())
                    ctx.channel().close()
                }

                DataFrameCode.ACCESS_SUCCESS.code ->{
                    log.info("授权成功")
                    log.info(msg.message)
                }

                DataFrameCode.REMOTE_CHANNEL_ACCEPT_TCP.code ->{
                    var client = tcpRemoteRequestMap[msg.tcpRemoteChannelId]
                    if (client == null){
                        var addrWithoutProtocol: String = msg.clientAddress
                        if (!addrWithoutProtocol.startsWith("http://") && !addrWithoutProtocol.startsWith("https://")) {
                            addrWithoutProtocol = "http://$addrWithoutProtocol"
                        }

                        val uri = URI.create(addrWithoutProtocol)
                        client = TcpRequestClient(msg.clientAddress,uri.host,uri.port,msg.tcpRemoteChannelId,msg.data.toByteArray())
                        tcpRemoteRequestMap[msg.tcpRemoteChannelId] = client
                    }
                    client.writeAndFlushWithQueue(msg.data.toByteArray())
                }

                DataFrameCode.REMOTE_CHANNEL_CLOSE_TCP.code ->{
                    tcpRemoteRequestMap[msg.tcpRemoteChannelId]?.close()

                    // 二次确认是否移除
                    tcpRemoteRequestMap.remove(msg.tcpRemoteChannelId)
                }

                DataFrameCode.REMOTE_CHANNEL_ACCEPT_UDP.code ->{
                    var client:UdpRequestClient? = null
                    udpRemoteRequestList.find { it.remoteHost == msg.udpRemoteIp && it.remotePort == msg.udpRemotePort }?.let {
                        client = it
                    }
                    if (client == null) {
                        var addrWithoutProtocol: String = msg.clientAddress
                        if (!addrWithoutProtocol.startsWith("http://") && !addrWithoutProtocol.startsWith("https://")) {
                            addrWithoutProtocol = "http://$addrWithoutProtocol"
                        }
                        val uri = URI.create(addrWithoutProtocol)
                        client = UdpRequestClient(msg.clientAddress,uri.host,uri.port,msg.udpRemoteIp,msg.udpRemotePort,msg.data.toByteArray())
                        udpRemoteRequestList.add(client!!)
                    }
                    client!!.writeAndFlush(msg.data.toByteArray())

                }

                DataFrameCode.CLIENT_FLOW_LIMIT.code ->{
                    log.error("流量超出限制:{}", msg.getMessage())
                }

                DataFrameCode.GET_CLIENT_CONNECTS.code ->{
                    val tcpRes = ArrayList<Map<String,String>>()
                    val udpRes = ArrayList<Map<String,String>>()
                    tcpRemoteRequestMap.forEach { (t, u) ->
                        tcpRes.add(mapOf("channelId" to t,"clientHost" to u.clientHost,"clientPort" to u.clientPort.toString()))
                    }
                    udpRemoteRequestList.forEach {
                        udpRes.add(mapOf("clientHost" to it.clientHost,"clientPort" to it.clientPort.toString(),"remoteHost" to it.remoteHost,"remotePort" to it.remotePort.toString()))
                    }
                    msg.toBuilder().setData(ByteString.copyFrom(ObjectMapper().writeValueAsBytes(mapOf("tcp" to tcpRes,"udp" to udpRes)))).build().let {
                        ctx.channel().writeAndFlush(it)
                    }
                }

                DataFrameCode.RESTART_CLIENT_CONNECT.code ->{
                    log.info("服务端要求重启")
                    ctx.channel().close()
                }

            }
        }
    }


    // 关闭所有程序已经连接到 backend 的连接, 并清理缓存区
    private fun closeAllConnect(){
        tcpRemoteRequestMap.forEach {
            kotlin.runCatching {
                it.value.close()
            }
        }
        tcpRemoteRequestMap.clear()
        udpRemoteRequestList.clear()
    }

}