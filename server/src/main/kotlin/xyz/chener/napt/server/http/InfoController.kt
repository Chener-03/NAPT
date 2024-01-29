package xyz.chener.napt.server.http

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.StringUtils
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.chener.napt.common.entity.DataFrameCode
import xyz.chener.napt.common.entity.DataFrameEntity
import xyz.chener.napt.server.core.ClientManager
import xyz.chener.napt.server.core.proxy.TcpPortProxy
import xyz.chener.napt.server.entity.ClientItem
import xyz.chener.napt.server.http.entity.SyncLockPayload
import xyz.chener.napt.server.repository.ClientItemRepository
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.jvmName


@RestController
@Validated
open class InfoController {

    @Value("\${napt.http.token}")
    lateinit var token: String

    @Autowired
    lateinit var clientManager: ClientManager

    @Autowired
    lateinit var clientItemRepository: ClientItemRepository

    @RequestMapping("/http/allEndpoints")
    fun allEndpoints(): List<String> {
        val list = ArrayList<String>()
        InfoController::class.functions.forEach {
            it.annotations.find {it0->it0.annotationClass.jvmName == RequestMapping::class.jvmName }?.let {ann->
                if (ann is RequestMapping){
                    list.add(ann.value[0])
                }
            }
        }
        return list
    }


    // 获取可视化的客户端信息
    @RequestMapping("/http/onlineClientInfoView")
    fun fullConnectInfo(response: HttpServletResponse): ResponseEntity<String> {
        response.setHeader("Content-Type", "application/json;charset=UTF-8")

        val sb = StringBuilder()

        clientManager.clientUidToClientChannelId.forEach {(clientUid,channelId)->
            sb.append("UID: $clientUid\n")
            sb.append("\t已监听的端口信息:\n")

            val backendContext = clientManager.channelIdToChannel[channelId]

            // 同步获取后端信息
            var clientResponse : String? = ""
            backendContext?.let {
                val uuid = UUID.randomUUID().toString()
                try {
                    val dataFrame: DataFrameEntity.DataFrame = DataFrameEntity.DataFrame.newBuilder()
                        .setCode(DataFrameCode.GET_CLIENT_CONNECTS.code)
                        .setMessage(uuid)
                        .build()
                    val lock = ReentrantLock()
                    val waitCondition = lock.newCondition()
                    lock.lock()
                    clientManager.getClientInfoLockCache[uuid] = SyncLockPayload(lock, waitCondition)
                    backendContext.channel().writeAndFlush(dataFrame)
                    waitCondition.await(5000, java.util.concurrent.TimeUnit.MILLISECONDS)
                }finally {
                    clientManager.getClientInfoLockCache.remove(uuid)?.let { slp->
                        clientResponse = slp.result
                        slp.lock.unlock()
                    }
                }

            }

            clientManager.portProxys[clientUid]?.forEach{
                sb.append("\t${if (it.proxyType.code == 1) "TCP" else "UDP"}  IP:${it.port} -> ${it.clientAddress}\n")
                if (it is TcpPortProxy) {
                    sb.append("\t\t服务端已建立的TCP连接:\n")
                    it.channelIdToContext.forEach { (channelId, context) ->
                        sb.append("\t\t\t$channelId\n")
                    }
                }
                sb.append("\n")
            }

            sb.append("\t后端信息:\n")
            val om = ObjectMapper()
            val pt = om.writerWithDefaultPrettyPrinter().writeValueAsString(om.readValue(clientResponse,Map::class.java))
            pt.split("\n").forEach {
                sb.append("\t\t$it\n")
            }

            sb.append("\n\n")
        }

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(sb.toString())
    }



    @RequestMapping("/http/restartClient")
    fun restartClient(@RequestParam("uid") uid: String): Boolean {
        if (!StringUtils.hasText(uid)){
            return false
        }

        clientManager.clientUidToClientChannelId[uid]?.let {
            clientManager.channelIdToChannel[it]?.let { channelHandlerContext ->
                val dataFrame: DataFrameEntity.DataFrame = DataFrameEntity.DataFrame.newBuilder()
                    .setCode(DataFrameCode.RESTART_CLIENT_CONNECT.code)
                    .setMessage(DataFrameCode.RESTART_CLIENT_CONNECT.message)
                    .build()
                channelHandlerContext.channel().writeAndFlush(dataFrame)
                return@restartClient true
            }
        }

        return false
    }


    @RequestMapping("/http/getClientInfo")
    fun getClientInfo() : List<ClientItem>{
        return clientItemRepository.findAll()
    }

    @RequestMapping("/http/deleteById")
    fun deleteById(@RequestParam("id") id: Long) : Boolean{
        clientItemRepository.deleteById(id)
        return true
    }


    @RequestMapping("/http/saveClientInfo")
    open fun saveClientInfo(@ModelAttribute @Validated clientItem: ClientItem) : Boolean{
        //save or update
        clientItemRepository.save(clientItem)
        return true
    }

}