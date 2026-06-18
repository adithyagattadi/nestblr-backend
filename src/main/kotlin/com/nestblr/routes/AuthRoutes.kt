package com.nestblr.routes

import com.nestblr.models.dto.ApiResponse
import com.nestblr.models.dto.RegisterRequest
import com.nestblr.plugins.BadRequestException
import com.nestblr.plugins.FirebasePrincipal
import com.nestblr.plugins.NotFoundException
import com.nestblr.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(userRepo: UserRepository) {
    route("/api/v1/auth") {

        // All routes here require a valid Firebase token
        authenticate("firebase") {

            // POST /api/v1/auth/register — link Firebase user to our DB
            post("/register") {
                val principal = call.principal<FirebasePrincipal>()
                    ?: throw BadRequestException("No authenticated user")

                val body = call.receive<RegisterRequest>()
                val role = body.role.uppercase()
                if (role !in listOf("TENANT", "OWNER")) {
                    throw BadRequestException("role must be TENANT or OWNER")
                }

                body.gender?.let {
                    if (it !in listOf("MALE", "FEMALE", "OTHER", "PREFER_NOT_TO_SAY")) {
                        throw BadRequestException("gender must be MALE, FEMALE, OTHER, or PREFER_NOT_TO_SAY")
                    }
                }
                body.dob?.let {
                    // ISO date format check — let Postgres do the rigorous parse, but reject obvious garbage early
                    if (!it.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
                        throw BadRequestException("dob must be ISO date format (YYYY-MM-DD)")
                    }
                    // 18+ check at backend too (defense in depth — Android does this client-side)
                    val parsedDob = try {
                        java.time.LocalDate.parse(it)
                    } catch (e: Exception) {
                        throw BadRequestException("dob is not a valid date")
                    }
                    val age = java.time.Period.between(parsedDob, java.time.LocalDate.now()).years
                    if (age < 18) throw BadRequestException("Users must be 18 or older")
                }

                val user = userRepo.createOrGet(
                    firebaseUid = principal.uid,
                    email = principal.email,
                    role = role,
                    fullName = body.fullName,
                    phone = body.phone,
                    gender = body.gender,    // NEW
                    dob = body.dob           // NEW
                )
                call.respond(HttpStatusCode.OK, ApiResponse(data = user))
            }

            // GET /api/v1/auth/me — get current user profile
            get("/me") {
                val principal = call.principal<FirebasePrincipal>()
                    ?: throw BadRequestException("No authenticated user")

                val user = userRepo.findByFirebaseUid(principal.uid)
                    ?: throw NotFoundException("User not registered. Call /register first.")

                call.respond(HttpStatusCode.OK, ApiResponse(data = user))
            }
        }
    }
}