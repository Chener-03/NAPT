package xyz.chener.napt.server.core

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Example
import org.springframework.data.domain.ExampleMatcher
import org.springframework.stereotype.Component
import xyz.chener.napt.common.entity.DataFrameCode
import xyz.chener.napt.common.entity.DataFrameEntity
import xyz.chener.napt.server.entity.ClientItem
import xyz.chener.napt.server.repository.ClientItemRepository
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock


@Component
class TrafficLimiter {


    @Value("\${napt.server.traffic-limiter:false}")
    var enabled : Boolean = false

    @Autowired
    lateinit var clientManager: ClientManager

    @Autowired
    lateinit var clientItemRepository: ClientItemRepository


    private var networkSpeedMap: MutableMap<String, SlidingWindowTrafficCalculator> = HashMap()


    // 检查流量计数器 通过则表示没有超出流量
    fun check(clientUid: String,clientAddress:String,port:Int, length: Int): Boolean {
        if (length == 0) return true

        postSpeed(clientUid,clientAddress,port,length)

        if (!enabled) return true

        val item = ClientItem().also {
            it.clientUid = clientUid
            it.clientAddr = clientAddress
            it.serverPort = port
        }

        val matcher = ExampleMatcher.matching()
            .withMatcher("clientUid", ExampleMatcher.GenericPropertyMatchers.exact())
            .withMatcher("clientAddr", ExampleMatcher.GenericPropertyMatchers.exact())
            .withMatcher("serverPort", ExampleMatcher.GenericPropertyMatchers.exact())

        val clientItems = clientItemRepository.findAll(Example.of(item, matcher))

        if (clientItems.isEmpty()) {
            sendToBackendClient(clientUid,clientAddress,port,"not found client item")
            return false
        }

        if (clientItems.size > 1) {
            sendToBackendClient(clientUid,clientAddress,port,"found too many client item")
            return false
        }

        val clientItem = clientItems[0]
        if (clientItem.maxFlowLimit == -1L) {
            return true
        }

        if (clientItem.maxFlowLimit!! < clientItem.flow!!) {
            sendToBackendClient(clientUid,clientAddress,port,"flow limit")
            return false
        }

        clientItemRepository.updateFlowByClient(length,clientUid,clientAddress,port)
        return true
    }

    fun getTrafficRate(clientUid: String,clientAddress:String,port:Int): Double {
        val key = "$clientUid-$clientAddress-$port"
        return networkSpeedMap[key]?.getTrafficRate() ?: 0.0
    }

    private fun sendToBackendClient(clientUid: String,clientAddress: String,port: Int,message:String){
        clientManager.clientUidToClientChannelId[clientUid]?.let {channelId ->
            clientManager.channelIdToChannel[channelId]?.let { channelHandlerContext ->
                val dataFrame: DataFrameEntity.DataFrame = DataFrameEntity.DataFrame.newBuilder()
                    .setCode(DataFrameCode.CLIENT_FLOW_LIMIT.code)
                    .setMessage("${DataFrameCode.CLIENT_FLOW_LIMIT.message} : server port $port,clientAddress $clientAddress, $message")
                    .build()
                channelHandlerContext.channel().writeAndFlush(dataFrame)
            }

        }
    }


    private fun postSpeed(clientUid: String,clientAddress: String,port: Int,length: Int) {
        val key = "$clientUid-$clientAddress-$port"
        networkSpeedMap[key]?.recordTraffic(length.toLong()) ?: kotlin.run {
            networkSpeedMap[key] = SlidingWindowTrafficCalculator()
            networkSpeedMap[key]?.recordTraffic(length.toLong())
        }
    }

}


class SlidingWindowTrafficCalculator() {
    private val window: MutableList<Pair<Long, Long>> = LinkedList()

    private val readWriteLock = ReentrantReadWriteLock()


    // 记录流量
    fun recordTraffic(bytes: Long) {
        try {
            readWriteLock.writeLock().lock()
            val currentTime = System.currentTimeMillis()
            val ct :Long = currentTime / 100

            if (window.isNotEmpty() && window.last().first == ct){
                val last = window.last()
                window.removeLast()
                window.addLast(ct to last.second + bytes)
            }else{
                window.addLast(ct to bytes)
            }

            removeExpiredTraffic(ct)
        } finally {
            readWriteLock.writeLock().unlock()
        }
    }

    // 获取当前的流量速率（字节/秒）
    fun getTrafficRate(): Double {
        try {
            readWriteLock.readLock().lock()
            val currentTime = System.currentTimeMillis()
            val ct :Long = currentTime / 100
            removeExpiredTraffic(ct)

            return window.sumOf { it.second }.toDouble()
        }finally {
            readWriteLock.readLock().unlock()
        }
    }

    private fun removeExpiredTraffic(currentTime: Long) {
        while (window.isNotEmpty() && currentTime - window.first().first > 10) {
            window.removeFirst()
        }
    }
}


