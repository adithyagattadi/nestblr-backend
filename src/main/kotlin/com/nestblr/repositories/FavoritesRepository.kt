package com.nestblr.repositories

import com.nestblr.config.DatabaseFactory.dbQuery
import com.nestblr.models.dto.FavoriteDto
import com.nestblr.models.dto.ListingSummaryDto
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.sql.Connection

/**
 * Tenant favorites. The favorites table is (user_id, listing_id, created_at)
 * with a composite PK on (user_id, listing_id) and ON DELETE CASCADE FKs to
 * both users and listings.
 */
class FavoritesRepository {

    /**
     * Guard for the POST endpoint: a favorite may only point at a live listing.
     * Note soft-deletes set status = 'DELETED' (no row removal), so the FK
     * cascade never fires for them — hence the explicit ACTIVE check here and
     * the status filter in [listForUser].
     */
    suspend fun isListingActive(listingId: String): Boolean = dbQuery {
        val conn: Connection = TransactionManager.current().connection.connection as Connection
        conn.prepareStatement(
            "SELECT 1 FROM listings WHERE id = ?::uuid AND status = 'ACTIVE'"
        ).use { stmt ->
            stmt.setString(1, listingId)
            stmt.executeQuery().use { rs -> rs.next() }
        }
    }

    /**
     * Adds the listing to the user's favorites and returns the row.
     * Idempotent: a re-favorite hits the PK conflict and the no-op UPDATE keeps
     * the original created_at, so RETURNING still yields the existing row.
     */
    suspend fun add(userId: String, listingId: String): FavoriteDto = dbQuery {
        val conn: Connection = TransactionManager.current().connection.connection as Connection
        val sql = """
            INSERT INTO favorites (user_id, listing_id)
            VALUES (?::uuid, ?::uuid)
            ON CONFLICT (user_id, listing_id) DO UPDATE SET created_at = favorites.created_at
            RETURNING listing_id, created_at
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, userId)
            stmt.setString(2, listingId)
            stmt.executeQuery().use { rs ->
                rs.next()
                FavoriteDto(
                    listingId = rs.getString("listing_id"),
                    createdAt = rs.getString("created_at")
                )
            }
        }
    }

    /** Removes a favorite. Returns whether a row was actually deleted (endpoint is idempotent regardless). */
    suspend fun remove(userId: String, listingId: String): Boolean = dbQuery {
        val conn: Connection = TransactionManager.current().connection.connection as Connection
        conn.prepareStatement(
            "DELETE FROM favorites WHERE user_id = ?::uuid AND listing_id = ?::uuid"
        ).use { stmt ->
            stmt.setString(1, userId)
            stmt.setString(2, listingId)
            stmt.executeUpdate() > 0
        }
    }

    /**
     * The user's favorited listings as [ListingSummaryDto], most recently favorited first.
     * Mirrors the search SELECT shape (min_rent / cover_photo subqueries) but without the
     * geo filter — there's no center point here, so distanceMeters is null. Soft-deleted
     * listings are filtered out rather than surfaced as broken cards. isFavorite is true
     * by definition for every row.
     */
    suspend fun listForUser(userId: String): List<ListingSummaryDto> = dbQuery {
        val conn: Connection = TransactionManager.current().connection.connection as Connection
        val sql = """
            SELECT l.id, l.title, l.locality, l.address_line,
                   ST_Y(l.location::geometry) AS lat, ST_X(l.location::geometry) AS lng,
                   l.gender_preference, l.pg_type, l.food_type,
                   l.avg_rating, l.review_count,
                   (SELECT MIN(monthly_rent) FROM room_options WHERE listing_id = l.id) AS min_rent,
                   (SELECT thumbnail_url FROM listing_photos
                     WHERE listing_id = l.id ORDER BY display_order LIMIT 1) AS cover_photo
            FROM favorites f
            INNER JOIN listings l ON l.id = f.listing_id
            WHERE f.user_id = ?::uuid AND l.status = 'ACTIVE'
            ORDER BY f.created_at DESC
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, userId)
            stmt.executeQuery().use { rs ->
                val out = mutableListOf<ListingSummaryDto>()
                while (rs.next()) {
                    out.add(
                        ListingSummaryDto(
                            id = rs.getString("id"),
                            title = rs.getString("title"),
                            locality = rs.getString("locality"),
                            addressLine = rs.getString("address_line"),
                            latitude = rs.getDouble("lat"),
                            longitude = rs.getDouble("lng"),
                            genderPreference = rs.getString("gender_preference") ?: "COED",
                            pgType = rs.getString("pg_type") ?: "PG",
                            foodType = rs.getString("food_type") ?: "BOTH",
                            avgRating = rs.getDouble("avg_rating"),
                            reviewCount = rs.getInt("review_count"),
                            minRent = (rs.getObject("min_rent") as? Number)?.toInt(),
                            coverPhotoUrl = rs.getString("cover_photo"),
                            distanceMeters = null,
                            isFavorite = true
                        )
                    )
                }
                out
            }
        }
    }
}
