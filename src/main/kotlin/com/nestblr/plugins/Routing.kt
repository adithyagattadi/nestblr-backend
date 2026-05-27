package com.nestblr.plugins

import com.nestblr.repositories.ListingRepository
import com.nestblr.routes.listingRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val listingRepository = ListingRepository()

    routing {
        listingRoutes(listingRepository)
    }
}
