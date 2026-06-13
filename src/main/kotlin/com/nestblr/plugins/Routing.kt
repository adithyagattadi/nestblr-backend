package com.nestblr.plugins

import com.nestblr.repositories.FavoritesRepository
import com.nestblr.repositories.InquiriesRepository
import com.nestblr.repositories.ListingRepository
import com.nestblr.repositories.OwnerListingRepository
import com.nestblr.repositories.ReviewsRepository
import com.nestblr.repositories.UserRepository
import com.nestblr.routes.authRoutes
import com.nestblr.routes.inquiryRoutes
import com.nestblr.routes.listingRoutes
import com.nestblr.routes.meRoutes
import com.nestblr.routes.ownerRoutes
import com.nestblr.routes.reviewRoutes
import io.ktor.server.application.*
import io.ktor.server.http.content.staticFiles
import io.ktor.server.routing.*
import java.io.File

fun Application.configureRouting(uploadsDir: File) {
    val listingRepository = ListingRepository()
    val userRepository = UserRepository()
    val ownerListingRepository = OwnerListingRepository()
    val favoritesRepository = FavoritesRepository()
    val reviewsRepository = ReviewsRepository()
    val inquiriesRepository = InquiriesRepository()

    routing {
        // Public — serve uploaded photos from disk. No auth required.
        staticFiles("/uploads", uploadsDir)

        listingRoutes(listingRepository, userRepository)
        authRoutes(userRepository)
        ownerRoutes(userRepository, ownerListingRepository)
        meRoutes(userRepository, favoritesRepository)
        reviewRoutes(userRepository, reviewsRepository)
        inquiryRoutes(userRepository, inquiriesRepository)
    }
}