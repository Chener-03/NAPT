package xyz.chener.napt.client.aot

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference
import java.util.*
import java.util.function.Consumer

class AotRuntime : RuntimeHintsRegistrar {

    val proxyClass: List<Class<*>> = Arrays.asList<Class<*>>(
    )


    val resourcePath: List<String> = mutableListOf(

    )

    val refClass: List<Class<*>> = Arrays.asList<Class<*>>(
        // netty
        io.netty.buffer.ByteBufAllocator::class.java,
        io.netty.buffer.ByteBufUtil::class.java,
        io.netty.channel.DefaultChannelConfig::class.java,
        io.netty.buffer.UnpooledByteBufAllocator::class.java,
        io.netty.channel.unix.PreferredDirectByteBufAllocator::class.java,
        io.netty.channel.PreferHeapByteBufAllocator::class.java,
        io.netty.buffer.PooledByteBufAllocator::class.java,
        io.netty.buffer.AbstractByteBufAllocator::class.java,
    )

    val refClassStr: List<String> = mutableListOf(
    )


    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {

        refClass.forEach(Consumer<Class<*>> { e: Class<*>? ->
            hints.reflection().registerType(
                e!!, MemberCategory.DECLARED_CLASSES,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )
        })

        refClassStr.forEach(Consumer<String> { e: String? ->
            hints.reflection().registerType(
                TypeReference.of(
                    e!!
                ), MemberCategory.DECLARED_CLASSES,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )
        })


        proxyClass.forEach(Consumer<Class<*>> { e: Class<*>? ->
            hints.proxies().registerJdkProxy(e)
        })

        resourcePath.forEach(Consumer<String> { e: String? ->
            hints.resources().registerPattern(
                e!!
            )
        })


    }
}