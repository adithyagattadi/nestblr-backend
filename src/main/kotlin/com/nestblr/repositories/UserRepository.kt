package com.nestblr.repositories

import com.nestblr.config.DatabaseFactory.dbQuery
import com.nestblr.models.dto.UserDto
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.sql.Connection

class UserRepository {

    suspend fun findByFirebaseUid(uid: String): UserDto? = dbQuery {
        val conn: Connection = TransactionManager.current().connection.connection as Connection
        val sql = """
            SELECT id, firebase_uid, email, phone, full_name, role, gender, dob, is_verified
            FROM users WHERE firebase_uid = ?
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, uid)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.toUserDto() else null
            }
        }
    }

    /**
     * Creates a user row linked to a Firebase UID, or returns the existing one.
     * Idempotent — calling register twice won't create duplicates.
     */
    suspend fun createOrGet(
        firebaseUid: String,
        email: String?,
        role: String,
        fullName: String?,
        phone: String?,
        gender: String? = null,    // NEW — defaults to null so existing callers unaffected
        dob: String? = null        // NEW — ISO date string "YYYY-MM-DD"
    ): UserDto = dbQuery {
        val conn: Connection = TransactionManager.current().connection.connection as Connection

        // Already exists?
        val existing = run {
            val sql = "SELECT id, firebase_uid, email, phone, full_name, role, gender, dob, is_verified FROM users WHERE firebase_uid = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, firebaseUid)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.toUserDto() else null }
            }
        }
        if (existing != null) return@dbQuery existing

        // Phone is NOT NULL in schema; synthesize a placeholder if not provided
        val safePhone = phone ?: "email:$firebaseUid"

        val insert = """
            INSERT INTO users (firebase_uid, email, phone, full_name, role, gender, dob, is_verified)
            VALUES (?, ?, ?, ?, ?, ?, CAST(? AS DATE), FALSE)
            RETURNING id, firebase_uid, email, phone, full_name, role, gender, dob, is_verified
        """.trimIndent()
        conn.prepareStatement(insert).use { stmt ->
            stmt.setString(1, firebaseUid)
            stmt.setString(2, email)
            stmt.setString(3, safePhone)
            stmt.setString(4, fullName)
            stmt.setString(5, role)
            stmt.setString(6, gender)
            stmt.setString(7, dob)   // String "YYYY-MM-DD" or null
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.toUserDto()
            }
        }
    }

    private fun java.sql.ResultSet.toUserDto() = UserDto(
        id = getString("id"),
        firebaseUid = getString("firebase_uid"),
        email = getString("email"),
        phone = getString("phone"),
        fullName = getString("full_name"),
        role = getString("role"),
        isVerified = getBoolean("is_verified"),
        gender = getString("gender"),                  // NEW
        dob = getDate("dob")?.toString()               // NEW — java.sql.Date.toString() is "YYYY-MM-DD"
    )
}