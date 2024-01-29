package xyz.chener.napt.server.core.proxy

import io.netty.channel.Channel
import io.netty.channel.nio.NioEventLoopGroup
import xyz.chener.napt.server.ApplicationContextHolder
import xyz.chener.napt.server.core.ClientManager
import xyz.chener.napt.server.core.TrafficLimiter
import xyz.chener.napt.common.entity.ProxyType
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.Volatile


abstract class AbstractPortProxy(
    val proxyType: ProxyType, val clientUid: String, val port: Int, val clientAddress:String
) {

    companion object {
        val bossGroup: NioEventLoopGroup = NioEventLoopGroup(1)
        val workGroup: NioEventLoopGroup = NioEventLoopGroup(5)

        fun createProxy(proxyType: ProxyType, clientUid: String, port: Int, clientAddress: String, speedLimit: Int): AbstractPortProxy {
            return when (proxyType) {
                ProxyType.TCP -> TcpPortProxy(proxyType, clientUid, port, clientAddress, speedLimit)
                ProxyType.UDP -> UdpPortProxy(proxyType, clientUid, port, clientAddress, speedLimit)
            }
        }
    }

    // 当前代理线程
    protected var thread: Thread? = null

    // 代理的服务端channel
    @Volatile
    protected var channel: Channel? = null

    // 是否启动
    @Volatile
    protected var isStart: Boolean = true

    protected val lock: Lock = ReentrantLock()

    // 速度限制
    protected var speedLimit: Int = 0


    @kotlin.jvm.Volatile
    private var clientManager : ClientManager? = null

    @kotlin.jvm.Volatile
    private var trafficLimiter : TrafficLimiter? = null

    protected fun getClientManager() : ClientManager {
        if (clientManager == null) {
            synchronized(this) {
                if (clientManager == null) {
                    clientManager = ApplicationContextHolder.applicationContext.getBean(ClientManager::class.java)
                }
            }
        }
        return clientManager!!
    }

    protected fun getTrafficLimiter() : TrafficLimiter {
        if (trafficLimiter == null) {
            synchronized(this) {
                if (trafficLimiter == null) {
                    trafficLimiter = ApplicationContextHolder.applicationContext.getBean(TrafficLimiter::class.java)
                }
            }
        }
        return trafficLimiter!!
    }


    fun stop() {
        // 防止多次重复关闭
        lock.lock()
        try {
            isStart = false
            channel?.close()
            thread?.interrupt()
            thread?.join(2000)
        } catch (ignored: Exception) {
        } finally {
            lock.unlock()
        }
    }

    abstract fun run()
}