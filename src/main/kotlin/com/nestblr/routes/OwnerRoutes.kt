package com.nestblr.routes

import com.nestblr.models.dto.ApiResponse
import com.nestblr.models.dto.CreateListingRequest
import com.nestblr.plugins.BadRequestException
import com.nestblr.plugins.FirebasePrincipal
import com.nestblr.plugins.NotFoundException
import com.nestblr.repositories.OwnerListingRepository
import com.nestblr.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Owner-only listing management.
 *
 * Authorization happens in two layers:
 *  1. authenticate("firebase") — must have a valid token (handled by the plugin)
 *  2. role check — the user row must have role = OWNER
 *  3. ownership check (edit/delete) — the listing's owner_id must match this user
 *
 * A simple exception type carries the 403 case.
 */
class ForbiddenException(message: String) : RuntimeException(message)

fun Route.ownerRoutes(
    userRepo: UserRepository,
    ownerRepo: OwnerListingRepository
) {
    route("/api/v1/owner") {
        authenticate("firebase") {

            // GET /api/v1/owner/listings — my listings
            get("/listings") {
                val (userId, _) = requireOwner(call, userRepo)
                val listings = ownerRepo.listByOwner(userId)
                call.respond(HttpStatusCode.OK, ApiResponse(data = listings))
            }

            // POST /api/v1/owner/listings — create
            post("/listings") {
                val (userId, _) = requireOwner(call, userRepo)
                val body = call.receive<CreateListingRequest>()
                validateListing(body)
                val newId = ownerRepo.createListing(userId, body)
                call.respond(HttpStatusCode.Created, ApiResponse(data = mapOf("id" to newId)))
            }

            // PUT /api/v1/owner/listings/{id} — edit
            put("/listings/{id}") {
                val (userId, _) = requireOwner(call, userRepo)
                val listingId = call.parameters["id"]
                    ?: throw BadRequestException("Missing listing id")

                val ownerId = ownerRepo.getOwnerId(listingId)
                    ?: throw NotFoundException("Listing not found")
                if (ownerId != userId) throw ForbiddenException("You don't own this listing")

                val body = call.receive<CreateListingRequest>()
                validateListing(body)
                ownerRepo.updateListing(listingId, body)
                call.respond(HttpStatusCode.OK, ApiResponse(data = mapOf("id" to listingId)))
            }

            // DELETE /api/v1/owner/listings/{id} — soft delete
            delete("/listings/{id}") {
                val (userId, _) = requireOwner(call, userRepo)
                val listingId = call.parameters["id"]
                    ?: throw BadRequestException("Missing listing id")

                val ownerId = ownerRepo.getOwnerId(listingId)
                    ?: throw NotFoundException("Listing not found")
                if (ownerId != userId) throw ForbiddenException("You don't own this listing")

                ownerRepo.softDeleteListing(listingId)
                call.respond(HttpStatusCode.OK, ApiResponse(data = mapOf("deleted" to listingId)))
            }
        }
    }
}

/**
 * Resolves the authenticated Firebase user to our internal user row,
 * and enforces that they are an OWNER. Returns (internalUserId, email).
 */
private suspend fun requireOwner(
    call: ApplicationCall,
    userRepo: UserRepository
): Pair<String, String?> {
    val principal = call.principal<FirebasePrincipal>()
        ?: throw BadRequestException("No authenticated user")

    val user = userRepo.findByFirebaseUid(principal.uid)
        ?: throw ForbiddenException("User not registered. Call /auth/register first.")

    if (user.role != "OWNER") {
        throw ForbiddenException("Only owners can manage listings")
    }
    return user.id to user.email
}

private fun validateListing(req: CreateListingRequest) {
    if (req.title.isBlank()) throw BadRequestException("Title is required")
    if (req.addressLine.isBlank()) throw BadRequestException("Address is required")
    if (req.locality.isBlank()) throw BadRequestException("Locality is required")
    if (req.genderPreference !in listOf("MALE", "FEMALE", "COED"))
        throw BadRequestException("Invalid gender preference")
    if (req.pgType !in listOf("PG", "HOSTEL", "COLIVING"))
        throw BadRequestException("Invalid PG type")
    if (req.foodType !in listOf("VEG", "NON_VEG", "BOTH", "NONE"))
        throw BadRequestException("Invalid food type")
    if (req.latitude < -90 || req.latitude > 90 || req.longitude < -180 || req.longitude > 180)
        throw BadRequestException("Invalid coordinates")
    req.roomOptions.forEach { room ->
        if (room.sharingType !in listOf("SINGLE", "DOUBLE", "TRIPLE", "QUAD"))
            throw BadRequestException("Invalid sharing type: ${room.sharingType}")
        if (room.monthlyRent <= 0) throw BadRequestException("Rent must be positive")
        if (room.availableBeds > room.totalBeds)
            throw BadRequestException("Available beds can't exceed total beds")
    }
}