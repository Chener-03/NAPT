package xyz.chener.napt.client


import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import xyz.chener.napt.common.utils.BuildInfoUtils
import java.io.ByteArrayInputStream
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

@SpringBootApplication
class ClientApplication

fun main(args: Array<String>) {
    BuildInfoUtils.printBuildInfo(ClientApplication::class.java)
    runApplication<ClientApplication>(*args)
}
