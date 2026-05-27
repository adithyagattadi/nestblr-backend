package com.nestblr.routes

import com.nestblr.models.dto.ApiResponse
import com.nestblr.models.dto.PageMeta
import com.nestblr.models.dto.SearchFilters
import com.nestblr.plugins.BadRequestException
import com.nestblr.repositories.ListingRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.listingRoutes(repo: ListingRepository) {
    route("/api/v1/listings") {

        // GET /api/v1/listings/search?lat=12.93&lng=77.62&radius_km=3&min_rent=5000...
        get("/search") {
            val lat = call.request.queryParameters["lat"]?.toDoubleOrNull()
                ?: throw BadRequestException("lat is required")
            val lng = call.request.queryParameters["lng"]?.toDoubleOrNull()
                ?: throw BadRequestException("lng is required")

            // Sanity bounds — keep Bengaluru-area for now, prevent garbage input
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

            val (results, total) = repo.search(filters)
            call.respond(
                HttpStatusCode.OK,
                ApiResponse(
                    data = results,
                    meta = PageMeta(filters.page, filters.size, total)
                )
            )
        }

        // Health check
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }
    }
}
