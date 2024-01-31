package xyz.chener.napt.server

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ImportRuntimeHints
import xyz.chener.napt.common.utils.BuildInfoUtils
import xyz.chener.napt.server.aot.AotRuntime
import xyz.chener.napt.server.entity.ClientItem
import xyz.chener.napt.server.repository.ClientItemRepository
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

@SpringBootApplication
@ImportRuntimeHints(AotRuntime::class)
open class ServerApplication


fun main(args: Array<String>) {
    BuildInfoUtils.printBuildInfo(ServerApplication::class.java)
    runApplication<ServerApplication>(*args)
}