package xyz.chener.napt.client


import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ImportRuntimeHints
import xyz.chener.napt.client.aot.AotRuntime
import xyz.chener.napt.common.utils.BuildInfoUtils

@SpringBootApplication
@ImportRuntimeHints(AotRuntime::class)
class ClientApplication

fun main(args: Array<String>) {
    BuildInfoUtils.printBuildInfo(ClientApplication::class.java)
    runApplication<ClientApplication>(*args)
}
