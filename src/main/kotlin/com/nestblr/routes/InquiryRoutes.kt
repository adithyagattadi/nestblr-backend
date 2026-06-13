package com.nestblr.routes

import com.nestblr.models.dto.ApiResponse
import com.nestblr.plugins.BadRequestException
import com.nestblr.plugins.NotFoundException
import com.nestblr.repositories.InquiriesRepository
import com.nestblr.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val INQUIRY_UUID_SHAPE = Regex("^[0-9a-fA-F-]{36}$")

fun Route.inquiryRoutes(
    userRepo: UserRepository,
    inquiriesRepo: InquiriesRepository
) {
    // POST /api/v1/listings/{listingId}/inquiries — tenant logs an inquiry attempt.
    route("/api/v1/listings/{listingId}/inquiries") {
        authenticate("firebase") {
            post {
                val (tenantId, _) = requireTenant(call, userRepo, message = "Only tenants can record inquiries")
                val listingId = call.parameters["listingId"]
                    ?: throw BadRequestException("Missing listing id")
                if (!listingId.matches(INQUIRY_UUID_SHAPE)) {
                    throw BadRequestException("Invalid listing id format")
                }

                // Existence vs active: null status -> 404, non-ACTIVE -> 400.
                val status = inquiriesRepo.getListingStatus(listingId)
                    ?: throw NotFoundException("Listing not found")
                if (status != "ACTIVE") {
                    throw BadRequestException("Listing is not active")
                }

                val inquiry = inquiriesRepo.upsertInquiry(tenantId, listingId)
                call.respond(HttpStatusCode.OK, ApiResponse(data = inquiry))
            }
        }
    }

    // GET /api/v1/owner/inquiries/summary — owner sees per-listing interest (no tenant identity).
    route("/api/v1/owner/inquiries") {
        authenticate("firebase") {
            get("/summary") {
                val (ownerId, _) = requireOwner(call, userRepo)
                val summary = inquiriesRepo.summaryForOwner(ownerId)
                call.respond(HttpStatusCode.OK, ApiResponse(data = summary))
            }
        }
    }
}
