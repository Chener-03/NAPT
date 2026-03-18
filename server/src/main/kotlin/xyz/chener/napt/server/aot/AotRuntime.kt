package xyz.chener.napt.server.aot

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference
import xyz.chener.napt.server.entity.ClientItem
import xyz.chener.napt.server.repository.ClientItemRepository
import java.util.*
import java.util.function.Consumer

class AotRuntime : RuntimeHintsRegistrar {

    val proxyClass: List<Class<*>> = Arrays.asList<Class<*>>(
        ClientItemRepository::class.java
    )


    val resourcePath: List<String> = mutableListOf(
        "logback.xml"
    )

    val refClass: List<Class<*>> = Arrays.asList<Class<*>>(
        ClientItem::class.java,

        // netty
        io.netty.buffer.ByteBufAllocator::class.java,
        io.netty.buffer.ByteBufUtil::class.java,
        io.netty.channel.DefaultChannelConfig::class.java,
        io.netty.buffer.UnpooledByteBufAllocator::class.java,
        io.netty.channel.unix.PreferredDirectByteBufAllocator::class.java,
        io.netty.channel.PreferHeapByteBufAllocator::class.java,
        io.netty.buffer.PooledByteBufAllocator::class.java,
        io.netty.buffer.AbstractByteBufAllocator::class.java,

        ClientItemRepository::class.java,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor.SpecificationFluentQuery::class.java,
    )

    val refClassStr: List<String> = mutableListOf(
        //spring jpa
        "org.springframework.data.domain.Unpaged"
    )


    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {

        refClass.forEach(Consumer<Class<*>> { e: Class<*> ->
            hints.reflection().registerType(
                e,
                MemberCategory.ACCESS_PUBLIC_FIELDS,
                MemberCategory.ACCESS_DECLARED_FIELDS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_DECLARED_METHODS
            )
        })

        refClassStr.forEach(Consumer { e: String ->
            hints.reflection().registerType(
                TypeReference.of(
                    e
                ),                 MemberCategory.ACCESS_PUBLIC_FIELDS,
                MemberCategory.ACCESS_DECLARED_FIELDS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_DECLARED_METHODS
            )
        })


        proxyClass.forEach(Consumer<Class<*>> { e: Class<*> ->
            hints.proxies().registerJdkProxy(e)
        })

        resourcePath.forEach(Consumer<String> { e: String ->
            hints.resources().registerPattern(
                e
            )
        })


    }
}