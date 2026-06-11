package com.nestblr.routes

import com.nestblr.models.dto.ApiResponse
import com.nestblr.plugins.BadRequestException
import com.nestblr.plugins.FirebasePrincipal
import com.nestblr.plugins.NotFoundException
import com.nestblr.repositories.FavoritesRepository
import com.nestblr.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val UUID_SHAPE = Regex("^[0-9a-fA-F-]{36}$")

fun Route.meRoutes(
    userRepo: UserRepository,
    favoritesRepo: FavoritesRepository
) {
    route("/api/v1/me") {
        authenticate("firebase") {

            // GET /api/v1/me/favorites — the user's favorited listings, newest first.
            get("/favorites") {
                val userId = resolveUserId(call, userRepo)
                val favorites = favoritesRepo.listForUser(userId)
                call.respond(HttpStatusCode.OK, ApiResponse(data = favorites))
            }

            // POST /api/v1/me/favorites/{listingId} — add to favorites (idempotent).
            post("/favorites/{listingId}") {
                val userId = resolveUserId(call, userRepo)
                val listingId = requireListingId(call)

                if (!favoritesRepo.isListingActive(listingId)) {
                    throw NotFoundException("Listing not found")
                }

                val favorite = favoritesRepo.add(userId, listingId)
                call.respond(HttpStatusCode.OK, ApiResponse(data = favorite))
            }

            // DELETE /api/v1/me/favorites/{listingId} — remove from favorites (idempotent).
            // Always 200 even if it wasn't favorited — "make this not a favorite" always succeeds.
            delete("/favorites/{listingId}") {
                val userId = resolveUserId(call, userRepo)
                val listingId = requireListingId(call)

                favoritesRepo.remove(userId, listingId)
                call.respond(HttpStatusCode.OK, ApiResponse(data = mapOf("deleted" to listingId)))
            }
        }
    }
}

private fun requireListingId(call: ApplicationCall): String {
    val listingId = call.parameters["listingId"]
        ?: throw BadRequestException("Missing listing id")
    if (!listingId.matches(UUID_SHAPE)) {
        throw BadRequestException("Invalid listing id format")
    }
    return listingId
}

/**
 * Resolves the authenticated Firebase user to our internal user id, creating a
 * TENANT row on first contact. createOrGet is idempotent and never overwrites an
 * existing user's role, so an OWNER browsing search keeps their role. Shared by
 * the favorites endpoints and the (now authenticated) listing search/detail routes.
 */
internal suspend fun resolveUserId(call: ApplicationCall, userRepo: UserRepository): String {
    val principal = call.principal<FirebasePrincipal>()
        ?: throw BadRequestException("No authenticated user")
    val user = userRepo.createOrGet(
        firebaseUid = principal.uid,
        email = principal.email,
        role = "TENANT",
        fullName = null,
        phone = null
    )
    return user.id
}
