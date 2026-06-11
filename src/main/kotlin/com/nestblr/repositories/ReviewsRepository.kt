package com.nestblr.repositories

import com.nestblr.config.DatabaseFactory.dbQuery
import com.nestblr.models.dto.ReviewDto
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.sql.Connection

/**
 * Tenant review submission. The reviews table already carries a UNIQUE
 * (listing_id, user_id) constraint and a CHECK (rating BETWEEN 1 AND 5), so the
 * upsert below relies on ON CONFLICT and the DB enforces rating as a backstop.
 *
 * Each mutation recomputes the parent listing's avg_rating / review_count in the
 * same transaction (dbQuery == one newSuspendedTransaction), so concurrent
 * reviews can't leave the aggregates inconsistent.
 */
class ReviewsRepository {

    /** Listing status, or null if it doesn't exist — lets the route split 404 vs 400. */
    suspend fun getListingStatus(listingId: String): String? = dbQuery {
        val conn: Connection = TransactionManager.current().connection.connection as Connection
        conn.prepareStatement("SELECT status FROM listings WHERE id = ?::uuid").use { stmt ->
            stmt.setString(1, listingId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString("status") else null }
        }
    }

    /**
     * Inserts or updates the caller's review and returns it (joined to the user's
     * name for the DTO). created_at is bumped to NOW() on update — an edited review
     * is treated as fresh and re-surfaces to the top. Aggregates recomputed inline.
     */
    suspend fun upsert(userId: String, listingId: String, rating: Int, comment: String): ReviewDto = dbQuery {
        val conn: Connection = TransactionManager.current().connection.connection as Connection
        val upsertSql = """
            WITH upserted AS (
                INSERT INTO reviews (listing_id, user_id, rating, comment)
                VALUES (?::uuid, ?::uuid, ?, ?)
                ON CONFLICT (listing_id, user_id) DO UPDATE
                    SET rating = EXCLUDED.rating, comment = EXCLUDED.comment, created_at = NOW()
                RETURNING id, user_id, rating, comment, stayed_from, stayed_until, created_at
            )
            SELECT up.id, u.full_name AS user_name, up.rating, up.comment,
                   up.stayed_from, up.stayed_until, up.created_at
            FROM upserted up
            INNER JOIN users u ON u.id = up.user_id
        """.trimIndent()

        val review = conn.prepareStatement(upsertSql).use { stmt ->
            stmt.setString(1, listingId)
            stmt.setString(2, userId)
            stmt.setInt(3, rating)
            stmt.setString(4, comment)
            stmt.executeQuery().use { rs ->
                rs.next()
                ReviewDto(
                    id = rs.getString("id"),
                    userId = userId,
                    userName = rs.getString("user_name"),
                    rating = rs.getInt("rating"),
                    comment = rs.getString("comment"),
                    stayedFrom = rs.getString("stayed_from"),
                    stayedUntil = rs.getString("stayed_until"),
                    createdAt = rs.getString("created_at")
                )
            }
        }

        recomputeAggregates(conn, listingId)
        review
    }

    /**
     * Deletes the caller's review for a listing. Returns whether a row was removed.
     * Aggregates are recomputed only when something was actually deleted.
     */
    suspend fun deleteByUser(userId: String, listingId: String): Boolean = dbQuery {
        val conn: Connection = TransactionManager.current().connection.connection as Connection
        val deleted = conn.prepareStatement(
            "DELETE FROM reviews WHERE user_id = ?::uuid AND listing_id = ?::uuid"
        ).use { stmt ->
            stmt.setString(1, userId)
            stmt.setString(2, listingId)
            stmt.executeUpdate() > 0
        }
        if (deleted) recomputeAggregates(conn, listingId)
        deleted
    }

    /**
     * Recomputes avg_rating / review_count from the current reviews rows.
     * Runs on the caller's connection so it shares the enclosing transaction.
     */
    private fun recomputeAggregates(conn: Connection, listingId: String) {
        val sql = """
            UPDATE listings SET
                avg_rating = (SELECT COALESCE(AVG(rating)::numeric(3,2), 0) FROM reviews WHERE listing_id = ?::uuid),
                review_count = (SELECT COUNT(*) FROM reviews WHERE listing_id = ?::uuid)
            WHERE id = ?::uuid
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, listingId)
            stmt.setString(2, listingId)
            stmt.setString(3, listingId)
            stmt.executeUpdate()
        }
    }
}
