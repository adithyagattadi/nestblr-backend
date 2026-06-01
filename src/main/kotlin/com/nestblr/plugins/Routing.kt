package com.nestblr.plugins

import com.nestblr.repositories.ListingRepository
import com.nestblr.repositories.OwnerListingRepository
import com.nestblr.repositories.UserRepository
import com.nestblr.routes.authRoutes
import com.nestblr.routes.listingRoutes
import com.nestblr.routes.ownerRoutes
import io.ktor.server.application.*
import io.ktor.server.http.content.staticFiles
import io.ktor.server.routing.*
import java.io.File

fun Application.configureRouting() {
    val listingRepository = ListingRepository()
    val userRepository = UserRepository()
    val ownerListingRepository = OwnerListingRepository()

    routing {
        // Public — serve uploaded photos from disk. No auth required.
        staticFiles("/uploads", File("uploads"))

        listingRoutes(listingRepository)
        authRoutes(userRepository)
        ownerRoutes(userRepository, ownerListingRepository)
    }
}