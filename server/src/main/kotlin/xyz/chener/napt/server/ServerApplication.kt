package xyz.chener.napt.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ImportRuntimeHints
import xyz.chener.napt.common.utils.BuildInfoUtils
import xyz.chener.napt.server.aot.AotRuntime

@SpringBootApplication
@ImportRuntimeHints(AotRuntime::class)
open class ServerApplication


fun main(args: Array<String>) {
    BuildInfoUtils.printBuildInfo(ServerApplication::class.java)
    runApplication<ServerApplication>(*args)
}