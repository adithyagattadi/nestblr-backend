package com.nestblr

import com.nestblr.config.DatabaseFactory
import com.nestblr.config.FirebaseConfig
import com.nestblr.config.PhotoStorageFactory
import com.nestblr.plugins.configureAuthentication
import com.nestblr.plugins.configureRouting
import com.nestblr.plugins.configureSerialization
import com.nestblr.plugins.configureStatusPages
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    EngineMain.main(args)
}
fun Application.module() {
    FirebaseConfig.init()           // must come before authentication
    DatabaseFactory.init(environment.config)
    val photoStorage = PhotoStorageFactory.create()
    configureSerialization()
    configureStatusPages()
    configureAuthentication()
    configureRouting(photoStorage)
}