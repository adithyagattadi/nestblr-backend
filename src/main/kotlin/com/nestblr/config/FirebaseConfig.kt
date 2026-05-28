package com.nestblr.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.io.FileInputStream

object FirebaseConfig {

    /**
     * Initializes the Firebase Admin SDK using a service account key.
     *
     * The path is read from the FIREBASE_CREDENTIALS env var, falling back to
     * a local file for development. NEVER commit the service account JSON.
     *
     * Reference: https://firebase.google.com/docs/admin/setup
     */
    fun init() {
        if (FirebaseApp.getApps().isNotEmpty()) return  // already initialized

        val credentialsPath = System.getenv("FIREBASE_CREDENTIALS")
            ?: "firebase-service-account.json"  // local dev fallback

        val serviceAccount = FileInputStream(credentialsPath)

        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
            .build()

        FirebaseApp.initializeApp(options)
    }
}