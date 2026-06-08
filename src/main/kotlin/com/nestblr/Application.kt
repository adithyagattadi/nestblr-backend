package com.nestblr

import com.nestblr.config.DatabaseFactory
import com.nestblr.config.FirebaseConfig
import com.nestblr.plugins.configureAuthentication
import com.nestblr.plugins.configureRouting
import com.nestblr.plugins.configureSerialization
import com.nestblr.plugins.configureStatusPages
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import java.io.File

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    FirebaseConfig.init()           // must come before authentication
    DatabaseFactory.init(environment.config)
    val uploadsDir = File(System.getProperty("user.dir"), "uploads").absoluteFile
    uploadsDir.mkdirs()
    log.info("Uploads dir: ${uploadsDir.absolutePath}")
    configureSerialization()
    configureStatusPages()
    configureAuthentication()
    configureRouting(uploadsDir)
}