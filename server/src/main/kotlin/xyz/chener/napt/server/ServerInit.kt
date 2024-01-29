package xyz.chener.napt.server

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import xyz.chener.napt.server.repository.ClientItemRepository


@Component
open class ServerInit : CommandLineRunner {


    @Autowired
    var clientItemRepository: ClientItemRepository? = null

    @Value("\${napt.http.token}")
    lateinit var token: String


    override fun run(vararg args: String?) {
        println("token: $token")
//        println(ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(clientItemRepository?.findAll()))
    }
}