package com.nestblr.plugins

import com.nestblr.repositories.ListingRepository
import com.nestblr.repositories.UserRepository
import com.nestblr.routes.authRoutes
import com.nestblr.routes.listingRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val listingRepository = ListingRepository()
    val userRepository = UserRepository()

    routing {
        listingRoutes(listingRepository)
        authRoutes(userRepository)
    }
}