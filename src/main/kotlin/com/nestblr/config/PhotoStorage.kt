package com.nestblr.config

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream

/**
 * Photo storage abstraction. Two impls:
 *  - LocalDiskStorage: writes to ./uploads/, serves via static route. Local dev.
 *  - SupabaseStorage: writes to Supabase Storage bucket, serves via public URL. Production.
 *
 * Selected at startup via PHOTO_STORAGE env var. Default "local".
 */
sealed interface PhotoStorage {
    /**
     * Save the given input stream as a photo. Returns the URL to store in DB.
     * For LocalDisk: returns "/uploads/{uuid}.{ext}" (relative).
     * For Supabase: returns "https://.../{uuid}.{ext}" (absolute).
     */
    suspend fun save(inputStream: InputStream, filename: String, contentType: String): String

    /**
     * Delete the photo at the given URL (whatever save() returned).
     * Best-effort — missing objects log a warning and return.
     */
    suspend fun delete(url: String)

    /**
     * Directory to serve as static content. LocalDisk returns the uploads folder.
     * Supabase returns null (no static route needed).
     */
    val staticDir: File?
}

class LocalDiskStorage(private val uploadsDir: File) : PhotoStorage {
    private val log = LoggerFactory.getLogger(LocalDiskStorage::class.java)

    init {
        uploadsDir.mkdirs()
        log.info("Photo storage: LOCAL DISK at ${uploadsDir.absolutePath}")
    }

    override val staticDir: File? = uploadsDir

    override suspend fun save(inputStream: InputStream, filename: String, contentType: String): String {
        val target = File(uploadsDir, filename)
        inputStream.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return "/uploads/$filename"
    }

    override suspend fun delete(url: String) {
        val name = url.substringAfterLast('/')
        val file = File(uploadsDir, name)
        if (file.exists() && !file.delete()) {
            log.warn("Failed to delete local photo: ${file.absolutePath}")
        }
    }
}

class SupabaseStorage(
    private val supabaseUrl: String,
    private val serviceRoleKey: String,
    private val bucket: String
) : PhotoStorage {
    private val log = LoggerFactory.getLogger(SupabaseStorage::class.java)
    private val client = HttpClient(CIO)
    private val publicUrlBase = "$supabaseUrl/storage/v1/object/public/$bucket"
    private val uploadUrlBase = "$supabaseUrl/storage/v1/object/$bucket"

    init {
        log.info("Photo storage: SUPABASE at $supabaseUrl/storage/v1/object/$bucket")
    }

    override val staticDir: File? = null

    override suspend fun save(inputStream: InputStream, filename: String, contentType: String): String {
        val bytes = inputStream.readBytes()
        val response = client.post("$uploadUrlBase/$filename") {
            header("Authorization", "Bearer $serviceRoleKey")
            header("Content-Type", contentType)
            header("x-upsert", "false")
            setBody(bytes)
        }
        if (response.status.value !in 200..299) {
            val body = response.bodyAsText()
            throw RuntimeException("Supabase upload failed: HTTP ${response.status.value} — $body")
        }
        return "$publicUrlBase/$filename"
    }

    override suspend fun delete(url: String) {
        val name = url.substringAfterLast('/')
        val response = client.delete("$uploadUrlBase/$name") {
            header("Authorization", "Bearer $serviceRoleKey")
        }
        if (response.status.value !in 200..299 && response.status.value != 404) {
            log.warn("Failed to delete Supabase photo $name: HTTP ${response.status.value}")
        }
    }
}

object PhotoStorageFactory {
    fun create(): PhotoStorage {
        val mode = System.getenv("PHOTO_STORAGE")?.lowercase() ?: "local"
        return when (mode) {
            "supabase" -> {
                val url = System.getenv("SUPABASE_URL")
                    ?: throw IllegalStateException("SUPABASE_URL env var required when PHOTO_STORAGE=supabase")
                val key = System.getenv("SUPABASE_SERVICE_ROLE_KEY")
                    ?: throw IllegalStateException("SUPABASE_SERVICE_ROLE_KEY env var required when PHOTO_STORAGE=supabase")
                val bucket = System.getenv("SUPABASE_STORAGE_BUCKET") ?: "listing-photos"
                SupabaseStorage(url, key, bucket)
            }
            "local", "" -> {
                val uploadsDir = File(System.getProperty("user.dir"), "uploads").absoluteFile
                LocalDiskStorage(uploadsDir)
            }
            else -> throw IllegalStateException("Unknown PHOTO_STORAGE value: '$mode' (expected 'local' or 'supabase')")
        }
    }
}
