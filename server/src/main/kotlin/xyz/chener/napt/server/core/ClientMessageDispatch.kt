package xyz.chener.napt.server.core

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import xyz.chener.napt.common.entity.DataFrameCode
import xyz.chener.napt.common.entity.DataFrameEntity


open class ClientMessageDispatch(val clientManager: ClientManager,val messageHandle: MessageHandle) : ChannelInboundHandlerAdapter() {

    private val log: Logger = LoggerFactory.getLogger(ClientMessageDispatch::class.java)



    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
        clientManager.onClientClose(ctx?.channel()?.id()?.asLongText()!!)
        ctx.channel()?.close()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        clientManager.onClientClose(ctx.channel()?.id()?.asLongText()!!)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is DataFrameEntity.DataFrame) {

            if (msg.code == DataFrameCode.ACCESS.code) messageHandle.access(ctx, msg, false)
            if (msg.code == DataFrameCode.ACCESS_FORCE.code) messageHandle.access(ctx, msg, true)
            if (msg.code == DataFrameCode.CLIENT_CHANNEL_ACCEPT_TCP.code) messageHandle.onTcpClientData(ctx, msg)
            if (msg.code == DataFrameCode.CLIENT_CLOSE_REMOTE_CHANNEL_TCP.code) messageHandle.onTcpClientCloseRemoteChannel(ctx, msg)
            if (msg.code == DataFrameCode.CLIENT_CHANNEL_ACCEPT_UDP.code) messageHandle.onUdpClientData(ctx, msg)
            if (msg.code == DataFrameCode.HEART_BEAT.code) messageHandle.onHeartBeatMessage(ctx, msg)
            if (msg.code == DataFrameCode.GET_CLIENT_CONNECTS.code) messageHandle.onClientSyncMessage(ctx, msg)

        } else {
            ctx.channel().close()
        }
    }
}

