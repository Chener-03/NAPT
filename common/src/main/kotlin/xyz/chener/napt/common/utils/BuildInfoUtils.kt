package xyz.chener.napt.common.utils

import java.io.ByteArrayInputStream
import java.util.*
import kotlin.reflect.KClass

class BuildInfoUtils {
    companion object {
        fun printBuildInfo(mainClass: Class<*>){
            val resourceAsStream = mainClass.classLoader.getResourceAsStream("META-INF/build-info.properties")
            resourceAsStream?.readAllBytes()?.let {
                Properties().let { p->
                    p.load(ByteArrayInputStream(it))
                    p.getProperty("build.version")?.let { version->
                        println("Build Version: $version")
                    }
                    p.getProperty("build.time")?.let { version->
                        println("Build Time: $version")
                    }
                }
            }
        }
    }
}