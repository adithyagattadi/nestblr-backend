package com.nestblr.routes

import com.nestblr.models.dto.ApiResponse
import com.nestblr.models.dto.CreateReviewRequest
import com.nestblr.plugins.BadRequestException
import com.nestblr.plugins.FirebasePrincipal
import com.nestblr.plugins.NotFoundException
import com.nestblr.repositories.ReviewsRepository
import com.nestblr.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val REVIEW_UUID_SHAPE = Regex("^[0-9a-fA-F-]{36}$")
private const val MAX_COMMENT_LENGTH = 1000

fun Route.reviewRoutes(
    userRepo: UserRepository,
    reviewsRepo: ReviewsRepository
) {
    route("/api/v1/listings/{listingId}/reviews") {
        authenticate("firebase") {

            // POST /api/v1/listings/{listingId}/reviews — submit or edit my review (upsert).
            post {
                val (userId, _) = requireTenant(call, userRepo)
                val listingId = requireListingId(call)

                // Existence vs active: null status -> 404, non-ACTIVE -> 400.
                val status = reviewsRepo.getListingStatus(listingId)
                    ?: throw NotFoundException("Listing not found")
                if (status != "ACTIVE") throw BadRequestException("Listing is not active")

                val body = call.receive<CreateReviewRequest>()
                if (body.rating !in 1..5) {
                    throw BadRequestException("rating must be between 1 and 5")
                }
                val comment = body.comment.trim()
                if (comment.isBlank()) {
                    throw BadRequestException("comment must not be blank")
                }
                if (comment.length > MAX_COMMENT_LENGTH) {
                    throw BadRequestException("comment must be at most $MAX_COMMENT_LENGTH characters")
                }

                val review = reviewsRepo.upsert(userId, listingId, body.rating, comment)
                call.respond(HttpStatusCode.OK, ApiResponse(data = review))
            }

            // DELETE /api/v1/listings/{listingId}/reviews/me — retract my review (idempotent).
            // No review -> still 200 with { "deleted": null }.
            delete("/me") {
                val (userId, _) = requireTenant(call, userRepo)
                val listingId = requireListingId(call)

                val deleted = reviewsRepo.deleteByUser(userId, listingId)
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(data = mapOf("deleted" to if (deleted) listingId else null))
                )
            }
        }
    }
}

private fun requireListingId(call: ApplicationCall): String {
    val listingId = call.parameters["listingId"]
        ?: throw BadRequestException("Missing listing id")
    if (!listingId.matches(REVIEW_UUID_SHAPE)) {
        throw BadRequestException("Invalid listing id format")
    }
    return listingId
}

/**
 * Resolves the authenticated Firebase user and enforces role = TENANT.
 * Mirrors requireOwner (OwnerRoutes.kt) — same principal -> user-row -> role-check
 * shape, same ForbiddenException -> 403 mapping. Returns (internalUserId, email).
 */
private suspend fun requireTenant(
    call: ApplicationCall,
    userRepo: UserRepository
): Pair<String, String?> {
    val principal = call.principal<FirebasePrincipal>()
        ?: throw BadRequestException("No authenticated user")

    val user = userRepo.findByFirebaseUid(principal.uid)
        ?: throw ForbiddenException("User not registered. Call /auth/register first.")

    if (user.role != "TENANT") {
        throw ForbiddenException("Only tenants can submit reviews")
    }
    return user.id to user.email
}
