package com.nestblr.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.InputStream

object FirebaseConfig {

    private val log = LoggerFactory.getLogger(FirebaseConfig::class.java)

    /**
     * Initializes the Firebase Admin SDK using a service account key.
     *
     * In production (Render) the full JSON content is provided via the
     * FIREBASE_SERVICE_ACCOUNT_JSON env var. Locally it falls back to a file on
     * disk. NEVER commit the service account JSON.
     *
     * Reference: https://firebase.google.com/docs/admin/setup
     */
    fun init() {
        if (FirebaseApp.getApps().isNotEmpty()) return  // already initialized

        val jsonContent = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON")
        val serviceAccountStream: InputStream = if (jsonContent != null && jsonContent.isNotBlank()) {
            // Production: env var contains the full JSON content
            log.info("Loading Firebase credentials from FIREBASE_SERVICE_ACCOUNT_JSON env var")
            jsonContent.byteInputStream()
        } else {
            // Local dev fallback: read from file on disk
            val path = "firebase-service-account.json"
            log.info("Loading Firebase credentials from file: $path")
            FileInputStream(path)
        }

        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
            .build()

        FirebaseApp.initializeApp(options)
    }
}
