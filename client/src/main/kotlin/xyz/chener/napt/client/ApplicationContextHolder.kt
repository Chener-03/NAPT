package xyz.chener.napt.client

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component


@Component
class ApplicationContextHolder : ApplicationContextAware {


    companion object {
        lateinit var applicationContext: ApplicationContext
    }

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        Companion.applicationContext = applicationContext
    }
}