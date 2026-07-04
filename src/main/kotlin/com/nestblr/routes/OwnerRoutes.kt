package com.nestblr.routes

import com.nestblr.models.dto.ApiResponse
import com.nestblr.models.dto.CreateListingRequest
import com.nestblr.models.dto.PhotoDto
import com.nestblr.config.PhotoStorage
import com.nestblr.models.dto.UpdateRoomAvailabilityRequest
import com.nestblr.plugins.BadRequestException
import com.nestblr.plugins.FirebasePrincipal
import com.nestblr.plugins.NotFoundException
import com.nestblr.repositories.OwnerListingRepository
import com.nestblr.repositories.UserRepository
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.util.UUID

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
    ownerRepo: OwnerListingRepository,
    photoStorage: PhotoStorage
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

            // PATCH /api/v1/owner/listings/{listingId}/rooms/{roomId} — update only
            // available_beds on one room. Lightweight alternative to the full PUT.
            patch("/listings/{listingId}/rooms/{roomId}") {
                val (userId, _) = requireOwner(call, userRepo)
                val listingId = call.parameters["listingId"]
                    ?: throw BadRequestException("Missing listing id")
                val roomId = call.parameters["roomId"]
                    ?: throw BadRequestException("Missing room id")

                // Ownership first — a non-owner must not learn whether a room exists.
                val ownerId = ownerRepo.getOwnerId(listingId)
                    ?: throw NotFoundException("Listing not found")
                if (ownerId != userId) throw ForbiddenException("You don't own this listing")

                // Existence + room-belongs-to-listing check (null = 404).
                val room = ownerRepo.getRoomForUpdate(listingId, roomId)
                    ?: throw NotFoundException("Room not found")

                if (room.listingStatus != "ACTIVE") {
                    throw BadRequestException("Listing is not active")
                }

                val body = call.receive<UpdateRoomAvailabilityRequest>()
                if (body.availableBeds < 0 || body.availableBeds > room.totalBeds) {
                    throw BadRequestException("availableBeds must be between 0 and totalBeds")
                }

                val updated = ownerRepo.updateRoomAvailability(roomId, body.availableBeds)
                call.respond(HttpStatusCode.OK, ApiResponse(data = updated))
            }

            // POST /api/v1/owner/listings/{listingId}/photos — upload one photo
            post("/listings/{listingId}/photos") {
                val (userId, _) = requireOwner(call, userRepo)
                val listingId = call.parameters["listingId"]
                    ?: throw BadRequestException("Missing listing id")

                val ownerId = ownerRepo.getOwnerId(listingId)
                    ?: throw NotFoundException("Listing not found")
                if (ownerId != userId) throw ForbiddenException("You don't own this listing")

                val existingCount = ownerRepo.getPhotoCount(listingId)
                if (existingCount >= MAX_PHOTOS_PER_LISTING) {
                    throw BadRequestException(
                        "Listing already has the maximum $MAX_PHOTOS_PER_LISTING photos"
                    )
                }

                val url = receiveImage(call, photoStorage)
                val photoId = ownerRepo.insertPhoto(
                    listingId = listingId,
                    url = url,
                    thumbnailUrl = url,
                    displayOrder = existingCount
                )
                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse(
                        data = PhotoDto(
                            id = photoId,
                            url = url,
                            thumbnailUrl = url,
                            displayOrder = existingCount
                        )
                    )
                )
            }

            // DELETE /api/v1/owner/listings/{listingId}/photos/{photoId}
            delete("/listings/{listingId}/photos/{photoId}") {
                val (userId, _) = requireOwner(call, userRepo)
                val listingId = call.parameters["listingId"]
                    ?: throw BadRequestException("Missing listing id")
                val photoId = call.parameters["photoId"]
                    ?: throw BadRequestException("Missing photo id")

                val ownerId = ownerRepo.getOwnerId(listingId)
                    ?: throw NotFoundException("Listing not found")
                if (ownerId != userId) throw ForbiddenException("You don't own this listing")

                val photoUrl = ownerRepo.getPhotoUrlForListing(photoId, listingId)
                    ?: throw NotFoundException("Photo not found")

                // Best-effort object delete. The storage impl handles URL parsing
                // and missing-object logging internally.
                photoStorage.delete(photoUrl)

                ownerRepo.deletePhoto(photoId)
                call.respond(HttpStatusCode.OK, ApiResponse(data = mapOf("deleted" to photoId)))
            }

            // POST /api/v1/owner/listings/{listingId}/photos/{photoId}/cover
            // Make this photo the cover (display_order = 0). Cover is derived from
            // display_order ASC, so this just reorders — no is_cover flag.
            post("/listings/{listingId}/photos/{photoId}/cover") {
                val (userId, _) = requireOwner(call, userRepo)
                val listingId = call.parameters["listingId"]
                    ?: throw BadRequestException("Missing listing id")
                val photoId = call.parameters["photoId"]
                    ?: throw BadRequestException("Missing photo id")

                // Ownership first — a non-owner must not learn whether a photo exists.
                val ownerId = ownerRepo.getOwnerId(listingId)
                    ?: throw NotFoundException("Listing not found")
                if (ownerId != userId) throw ForbiddenException("You don't own this listing")

                // Photo must belong to the listing (null = 404).
                ownerRepo.getPhotoDisplayOrder(listingId, photoId)
                    ?: throw NotFoundException("Photo not found")

                if (ownerRepo.getListingStatus(listingId) != "ACTIVE") {
                    throw BadRequestException("Listing is not active")
                }

                // Atomic shift + set; idempotent no-op if already the cover.
                val photos = ownerRepo.setCoverPhoto(listingId, photoId)
                call.respond(HttpStatusCode.OK, ApiResponse(data = photos))
            }
        }
    }
}

private const val MAX_PHOTOS_PER_LISTING = 6
private const val MAX_PHOTO_BYTES = 5L * 1024 * 1024
private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

/**
 * Reads exactly one file part from the multipart body, validates it, and hands
 * it to [PhotoStorage]. Returns the URL the storage impl assigned (relative for
 * local disk, absolute for Supabase).
 *
 * Validation:
 *  - content-type must start with image/
 *  - extension must be jpg/jpeg/png/webp
 *  - bytes are buffered in memory bounded at 5 MB; oversize uploads are 400'd
 *    BEFORE any storage write, so we never half-upload a giant file to the cloud
 */
private suspend fun receiveImage(call: ApplicationCall, storage: PhotoStorage): String {
    val multipart = call.receiveMultipart()
    var url: String? = null

    multipart.forEachPart { part ->
        try {
            if (part is PartData.FileItem && url == null) {
                val contentType = part.contentType?.toString().orEmpty()
                if (!contentType.startsWith("image/")) {
                    throw BadRequestException("File must be an image (content-type was '$contentType')")
                }
                val originalName = part.originalFileName ?: "upload"
                val ext = originalName.substringAfterLast('.', "").lowercase()
                if (ext !in ALLOWED_EXTENSIONS) {
                    throw BadRequestException(
                        "Allowed extensions: ${ALLOWED_EXTENSIONS.joinToString()}"
                    )
                }
                val filename = "${UUID.randomUUID()}.$ext"

                // Read bytes with 5 MB cap, in memory (was streaming to disk before).
                val bytes = part.provider().toInputStream().use { input ->
                    val buf = ByteArray(8192)
                    val out = java.io.ByteArrayOutputStream()
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > MAX_PHOTO_BYTES) {
                            throw BadRequestException("Max file size is 5 MB")
                        }
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }

                url = storage.save(bytes.inputStream(), filename, contentType)
            }
        } finally {
            part.release()
        }
    }

    return url ?: throw BadRequestException("No file provided")
}

/**
 * Resolves the authenticated Firebase user to our internal user row,
 * and enforces that they are an OWNER. Returns (internalUserId, email).
 */
internal suspend fun requireOwner(
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