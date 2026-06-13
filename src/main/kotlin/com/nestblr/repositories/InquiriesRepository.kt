package com.nestblr.repositories

import com.nestblr.config.DatabaseFactory.dbQuery
import com.nestblr.models.dto.InquiryDto
import com.nestblr.models.dto.InquirySummaryDto
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.sql.Connection

/**
 * Inquiry tracking. One row per (tenant_id, listing_id) — enforced by the
 * inquiries_tenant_listing_unique constraint — so a repeat inquiry just bumps
 * last_attempt_at via ON CONFLICT. The owner-facing view is aggregated by
 * listing and never exposes tenant identity.
 */
class InquiriesRepository {

    /** Listing status, or null if it doesn't exist — lets the route split 404 vs 400. */
    suspend fun getListingStatus(listingId: String): String? = dbQuery {
        val conn: Connection = TransactionManager.current().connection.connection as Connection
        conn.prepareStatement("SELECT status FROM listings WHERE id = ?::uuid").use { stmt ->
            stmt.setString(1, listingId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString("status") else null }
        }
    }

    /**
     * Logs an inquiry attempt. Idempotent per (tenant, listing): the first call
     * inserts, repeats only bump last_attempt_at (created_at stays the first attempt).
     */
    suspend fun upsertInquiry(tenantId: String, listingId: String): InquiryDto = dbQuery {
        val conn: Connection = TransactionManager.current().connection.connection as Connection
        val sql = """
            INSERT INTO inquiries (tenant_id, listing_id, last_attempt_at)
            VALUES (?::uuid, ?::uuid, NOW())
            ON CONFLICT (tenant_id, listing_id) DO UPDATE SET last_attempt_at = NOW()
            RETURNING listing_id, tenant_id, created_at, last_attempt_at
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, tenantId)
            stmt.setString(2, listingId)
            stmt.executeQuery().use { rs ->
                rs.next()
                InquiryDto(
                    listingId = rs.getString("listing_id"),
                    tenantId = rs.getString("tenant_id"),
                    createdAt = rs.getString("created_at"),
                    lastAttemptAt = rs.getString("last_attempt_at")
                )
            }
        }
    }

    /**
     * Per-listing inquiry summary for [ownerId]'s listings, most recently active first.
     * Counts distinct inquirers (no identities exposed) and the latest attempt time.
     * Capped at 100 listings — no pagination for v1.
     */
    suspend fun summaryForOwner(ownerId: String): List<InquirySummaryDto> = dbQuery {
        val conn: Connection = TransactionManager.current().connection.connection as Connection
        val sql = """
            SELECT l.id   AS listing_id,
                   l.title AS listing_title,
                   COUNT(DISTINCT i.tenant_id) AS unique_inquirer_count,
                   MAX(i.last_attempt_at)      AS last_inquiry_at
            FROM inquiries i
            INNER JOIN listings l ON l.id = i.listing_id
            WHERE l.owner_id = ?::uuid
            GROUP BY l.id, l.title
            ORDER BY MAX(i.last_attempt_at) DESC
            LIMIT 100
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, ownerId)
            stmt.executeQuery().use { rs ->
                val out = mutableListOf<InquirySummaryDto>()
                while (rs.next()) {
                    out.add(
                        InquirySummaryDto(
                            listingId = rs.getString("listing_id"),
                            listingTitle = rs.getString("listing_title"),
                            uniqueInquirerCount = rs.getInt("unique_inquirer_count"),
                            lastInquiryAt = rs.getString("last_inquiry_at")
                        )
                    )
                }
                out
            }
        }
    }
}
