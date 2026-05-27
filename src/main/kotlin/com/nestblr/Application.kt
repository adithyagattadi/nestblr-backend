package com.nestblr

import com.nestblr.config.DatabaseFactory
import com.nestblr.plugins.configureRouting
import com.nestblr.plugins.configureSerialization
import com.nestblr.plugins.configureStatusPages
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    DatabaseFactory.init(environment.config)
    configureSerialization()
    configureStatusPages()
    configureRouting()
}
