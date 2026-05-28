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

                val user = userRepo.createOrGet(
                    firebaseUid = principal.uid,
                    email = principal.email,
                    role = role,
                    fullName = body.fullName,
                    phone = body.phone
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