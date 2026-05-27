package com.nestblr.repositories

import com.nestblr.config.DatabaseFactory.dbQuery
import com.nestblr.models.dto.ListingSummaryDto
import com.nestblr.models.dto.SearchFilters
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.sql.Connection
import java.sql.ResultSet

class ListingRepository {

    /**
     * Geo-search using PostGIS ST_DWithin + ST_Distance.
     *
     * Why ST_DWithin and not just ST_Distance < x:
     * ST_DWithin uses the GIST spatial index. ST_Distance < x cannot.
     * Reference: https://postgis.net/docs/ST_DWithin.html
     */
    suspend fun search(filters: SearchFilters): Pair<List<ListingSummaryDto>, Int> = dbQuery {
        val radiusMeters = filters.radiusKm * 1000

        val filterClauses = mutableListOf<String>()
        if (filters.gender != null) filterClauses.add("l.gender_preference = '${filters.gender.sanitize()}'")
        if (filters.food != null) filterClauses.add("l.food_type = '${filters.food.sanitize()}'")
        if (filters.pgType != null) filterClauses.add("l.pg_type = '${filters.pgType.sanitize()}'")

        val rentJoin = if (filters.minRent != null || filters.maxRent != null) {
            "INNER JOIN room_options r ON r.listing_id = l.id"
        } else {
            "LEFT JOIN room_options r ON r.listing_id = l.id"
        }
        if (filters.minRent != null) filterClauses.add("r.monthly_rent >= ${filters.minRent}")
        if (filters.maxRent != null) filterClauses.add("r.monthly_rent <= ${filters.maxRent}")

        val extraFilters = if (filterClauses.isNotEmpty()) "AND " + filterClauses.joinToString(" AND ") else ""
        val offset = filters.page * filters.size

        val sql = """
            WITH filtered_listings AS (
                SELECT DISTINCT l.id,
                                l.title,
                                l.locality,
                                l.address_line,
                                ST_Y(l.location::geometry) AS lat,
                                ST_X(l.location::geometry) AS lng,
                                l.gender_preference,
                                l.pg_type,
                                l.food_type,
                                l.avg_rating,
                                l.review_count,
                                ST_Distance(l.location, ST_GeogFromText(?)) AS dist
                FROM listings l
                $rentJoin
                WHERE l.status = 'ACTIVE'
                  AND ST_DWithin(l.location, ST_GeogFromText(?), ?)
                  $extraFilters
            )
            SELECT fl.*,
                   (SELECT MIN(monthly_rent) FROM room_options WHERE listing_id = fl.id) AS min_rent,
                   (SELECT thumbnail_url FROM listing_photos
                     WHERE listing_id = fl.id ORDER BY display_order LIMIT 1) AS cover_photo
            FROM filtered_listings fl
            ORDER BY fl.dist ASC
            LIMIT ? OFFSET ?
        """.trimIndent()

        val countSql = """
            SELECT COUNT(DISTINCT l.id) AS cnt
            FROM listings l
            $rentJoin
            WHERE l.status = 'ACTIVE'
              AND ST_DWithin(l.location, ST_GeogFromText(?), ?)
              $extraFilters
        """.trimIndent()

        val point = "POINT(${filters.lng} ${filters.lat})"
        val jdbcConn: Connection = TransactionManager.current().connection.connection as Connection

        val results = mutableListOf<ListingSummaryDto>()
        var total = 0

        jdbcConn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, point)
            stmt.setString(2, point)
            stmt.setDouble(3, radiusMeters)
            stmt.setInt(4, filters.size)
            stmt.setInt(5, offset)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    results.add(rs.toListingSummary())
                }
            }
        }

        jdbcConn.prepareStatement(countSql).use { stmt ->
            stmt.setString(1, point)
            stmt.setDouble(2, radiusMeters)
            stmt.executeQuery().use { rs ->
                if (rs.next()) total = rs.getInt("cnt")
            }
        }

        results to total
    }

    private fun String.sanitize() = this.uppercase().replace(Regex("[^A-Z_]"), "")

    private fun ResultSet.toListingSummary() = ListingSummaryDto(
        id = getString("id"),
        title = getString("title"),
        locality = getString("locality"),
        addressLine = getString("address_line"),
        latitude = getDouble("lat"),
        longitude = getDouble("lng"),
        genderPreference = getString("gender_preference") ?: "COED",
        pgType = getString("pg_type") ?: "PG",
        foodType = getString("food_type") ?: "BOTH",
        avgRating = getDouble("avg_rating"),
        reviewCount = getInt("review_count"),
        minRent = (getObject("min_rent") as? Number)?.toInt(),
        coverPhotoUrl = getString("cover_photo"),
        distanceMeters = getDouble("dist")
    )
}