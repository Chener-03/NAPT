package xyz.chener.napt.server.core

import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component
import xyz.chener.napt.common.entity.DataFrameCode
import xyz.chener.napt.common.entity.DataFrameEntity
import xyz.chener.napt.common.entity.ProxyType
import xyz.chener.napt.server.core.proxy.AbstractPortProxy
import xyz.chener.napt.server.entity.ClientItem
import xyz.chener.napt.server.http.entity.SyncLockPayload
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList


@Component
open class ClientManager {


    // clientUid --> clientChannelId
    val clientUidToClientChannelId: ConcurrentHashMap<String, String> = ConcurrentHashMap()


    // channelId -> ChannelHandlerContext
    val channelIdToChannel: ConcurrentHashMap<String, ChannelHandlerContext> = ConcurrentHashMap()


    // clientUid -> 端口代理   存储每个客户端的转发服务的服务端port列表
    val portProxys: ConcurrentHashMap<String, CopyOnWriteArrayList<AbstractPortProxy>> = ConcurrentHashMap<String, CopyOnWriteArrayList<AbstractPortProxy>>()

    // 同步获取后端信息  使用的等待器  供HTTP部分使用
    val getClientInfoLockCache = ConcurrentHashMap<String, SyncLockPayload>()

    /**
     * 后端客户端连接断开时执行的清理方法
     */
    fun onClientClose(clientChannelId :String){
        val clientUid = findClientUidByClientChannelId(clientChannelId)
        clientUid?.let {
            clientUidToClientChannelId.remove(it)

            // 把所有监听的代理关闭..
            portProxys[clientUid]?.forEach { it0 ->
                it0.runCatching {
                    this.stop()
                }
            }

            portProxys[clientUid]?.clear()
            portProxys.remove(clientUid)
        }
        channelIdToChannel.remove(clientChannelId)
    }


    /**
     * 踢出客户端
     */
    fun kickClient(clientUid: String){
        val channelId = clientUidToClientChannelId[clientUid]

        channelId?.let {
            val channelHandlerContext = channelIdToChannel[channelId]
            channelHandlerContext?.channel()?.writeAndFlush(
                DataFrameEntity.DataFrame.newBuilder()
                .setCode(DataFrameCode.CLOSE.code)
                .setMessage("${DataFrameCode.CLOSE.message}:其它客户端登录,强制断开连接")
                .build())
            channelHandlerContext?.channel()?.close()
            onClientClose(channelId)
        }
    }


    /**
     * 授权客户端
     */
    fun authClient(context : ChannelHandlerContext,clientUid: String,clientItems:List<ClientItem>){

        val message = StringBuilder().also { str ->
             clientItems.forEach {
                 str.append("IP:").append(it.serverPort).append(" --> ").append(it.clientAddr).append("\n")
             }
        }

        context.channel().writeAndFlush(
            DataFrameEntity.DataFrame.newBuilder()
                .setCode(DataFrameCode.ACCESS_SUCCESS.code)
                .setMessage(message.toString())
                .build()
        )

        clientUidToClientChannelId[clientUid] = context.channel().id().asLongText()
        channelIdToChannel[context.channel().id().asLongText()] = context

        val proxys = CopyOnWriteArrayList<AbstractPortProxy>().also { proxy ->
            // 启动代理端口
            clientItems.forEach {
                val proxyType = when(it.requestNtType){
                    1 -> ProxyType.TCP
                    2 -> ProxyType.UDP
                    else -> throw Exception("未知的代理类型:${it.requestNtType}")
                }
                val portProxy = AbstractPortProxy.createProxy(proxyType,clientUid,it.serverPort!!,it.clientAddr!!,it.speedLimit!!.toInt())
                proxy.add(portProxy)
            }
        }

        portProxys[clientUid] = proxys
    }

    private fun findClientUidByClientChannelId(clientChannelId: String): String? {
        return clientUidToClientChannelId.entries.find { it.value == clientChannelId }?.key
    }
}