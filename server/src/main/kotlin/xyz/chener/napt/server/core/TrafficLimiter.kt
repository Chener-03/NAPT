package xyz.chener.napt.server.core

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import xyz.chener.napt.server.repository.ClientItemRepository


@Component
class TrafficLimiter {


    @Value("\${napt.server.traffic-limiter:false}")
    var enabled : Boolean = false

    @Autowired
    lateinit var clientManager: ClientManager

    @Autowired
    lateinit var clientItemRepository: ClientItemRepository


    // 检查流量计数器 通过则表示没有超出流量
    fun check(clientUid: String,clientAddress:String,port:Int, length: Int): Boolean {
        if (!enabled || length == 0) {
            return true
        }


        return true
    }

}