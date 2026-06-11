package com.nestblr.routes

import com.nestblr.models.dto.ApiResponse
import com.nestblr.models.dto.PageMeta
import com.nestblr.models.dto.SearchFilters
import com.nestblr.plugins.BadRequestException
import com.nestblr.plugins.NotFoundException
import com.nestblr.repositories.ListingRepository
import com.nestblr.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.listingRoutes(repo: ListingRepository, userRepo: UserRepository) {
    route("/api/v1/listings") {

        // Health check — public, no auth (used for liveness probes).
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        // Search + detail now require a Firebase token so the response can carry
        // per-user isFavorite. The Android client already sends the token on every
        // request, so this is non-breaking for our client.
        authenticate("firebase") {

            // GET /api/v1/listings/search?lat=12.93&lng=77.62&radius_km=3...
            get("/search") {
                val userId = resolveUserId(call, userRepo)

                val lat = call.request.queryParameters["lat"]?.toDoubleOrNull()
                    ?: throw BadRequestException("lat is required")
                val lng = call.request.queryParameters["lng"]?.toDoubleOrNull()
                    ?: throw BadRequestException("lng is required")

                if (lat !in 8.0..37.0 || lng !in 68.0..98.0) {
                    throw BadRequestException("Coordinates out of India bounds")
                }

                val filters = SearchFilters(
                    lat = lat,
                    lng = lng,
                    radiusKm = call.request.queryParameters["radius_km"]?.toDoubleOrNull() ?: 5.0,
                    minRent = call.request.queryParameters["min_rent"]?.toIntOrNull(),
                    maxRent = call.request.queryParameters["max_rent"]?.toIntOrNull(),
                    gender = call.request.queryParameters["gender"],
                    food = call.request.queryParameters["food"],
                    pgType = call.request.queryParameters["pg_type"],
                    page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0,
                    size = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 50) ?: 20
                )

                val (results, total) = repo.search(userId, filters)
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        data = results,
                        meta = PageMeta(filters.page, filters.size, total)
                    )
                )
            }

            // GET /api/v1/listings/{id} — full listing detail
            get("/{id}") {
                val userId = resolveUserId(call, userRepo)

                val id = call.parameters["id"]
                    ?: throw BadRequestException("listing id required")

                // Validate UUID shape early — saves a DB round-trip on garbage input
                if (!id.matches(Regex("^[0-9a-fA-F-]{36}$"))) {
                    throw BadRequestException("Invalid listing id format")
                }

                val detail = repo.findById(userId, id)
                    ?: throw NotFoundException("Listing not found")

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(data = detail)
                )
            }
        }
    }
}
