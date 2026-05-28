package com.nestblr.plugins

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*

/**
 * Principal holding the verified Firebase UID for the current request.
 */
data class FirebasePrincipal(
    val uid: String,
    val email: String?
)

/**
 * Custom Firebase token authentication.
 *
 * We use Ktor's `bearer` auth provider. The token is the Firebase ID token
 * sent by the Android client. We verify it with the Firebase Admin SDK.
 *
 * Reference: https://firebase.google.com/docs/auth/admin/verify-id-tokens
 */
fun Application.configureAuthentication() {
    install(Authentication) {
        bearer("firebase") {
            authenticate { credential ->
                try {
                    val decoded: FirebaseToken =
                        FirebaseAuth.getInstance().verifyIdToken(credential.token)
                    FirebasePrincipal(uid = decoded.uid, email = decoded.email)
                } catch (e: Exception) {
                    // Invalid or expired token → null means 401
                    null
                }
            }
        }
    }
}