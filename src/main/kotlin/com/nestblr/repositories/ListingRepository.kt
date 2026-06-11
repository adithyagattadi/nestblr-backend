package com.nestblr.repositories

import com.nestblr.config.DatabaseFactory.dbQuery
import com.nestblr.models.dto.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.sql.Connection
import java.sql.ResultSet

class ListingRepository {

    /**
     * Geo-search using PostGIS ST_DWithin + ST_Distance.
     * Reference: https://postgis.net/docs/ST_DWithin.html
     */
    suspend fun search(userId: String, filters: SearchFilters): Pair<List<ListingSummaryDto>, Int> = dbQuery {
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
                SELECT DISTINCT l.id, l.title, l.locality, l.address_line,
                                ST_Y(l.location::geometry) AS lat,
                                ST_X(l.location::geometry) AS lng,
                                l.gender_preference, l.pg_type, l.food_type,
                                l.avg_rating, l.review_count,
                                ST_Distance(l.location, ST_GeogFromText(?)) AS dist,
                                (f.user_id IS NOT NULL) AS is_favorite
                FROM listings l
                $rentJoin
                LEFT JOIN favorites f ON f.listing_id = l.id AND f.user_id = ?::uuid
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
            stmt.setString(1, point)       // ST_Distance reference point (SELECT)
            stmt.setString(2, userId)      // favorites LEFT JOIN user_id
            stmt.setString(3, point)       // ST_DWithin reference point (WHERE)
            stmt.setDouble(4, radiusMeters)
            stmt.setInt(5, filters.size)
            stmt.setInt(6, offset)
            stmt.executeQuery().use { rs ->
                while (rs.next()) results.add(rs.toListingSummary())
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

    /**
     * Fetch one listing with all related data.
     *
     * Why multiple queries instead of one giant JOIN:
     * A single JOIN with photos+rooms+amenities+reviews causes row explosion.
     * 5 photos × 3 rooms × 4 amenities × 5 reviews = 300 duplicated rows per listing,
     * requiring deduplication in code. Multiple targeted queries are clearer and
     * usually faster in practice because each query is smaller and uses tight indexes.
     *
     * All queries share the same DB connection inside the transaction, so this
     * is still one logical unit of work.
     */
    suspend fun findById(userId: String, id: String): ListingDetailDto? = dbQuery {
        val jdbcConn: Connection = TransactionManager.current().connection.connection as Connection

        // 1. Core listing + owner — one JOIN since each listing has exactly one owner
        val coreSql = """
            SELECT l.id, l.title, l.description, l.address_line, l.locality, l.city,
                   l.pincode, ST_Y(l.location::geometry) AS lat, ST_X(l.location::geometry) AS lng,
                   l.gender_preference, l.pg_type, l.food_type,
                   l.avg_rating, l.review_count, l.status,
                   u.id AS owner_id, u.full_name AS owner_name,
                   COALESCE(l.contact_phone, u.phone) AS owner_phone, u.is_verified AS owner_verified,
                   (fav.user_id IS NOT NULL) AS is_favorite
            FROM listings l
            INNER JOIN users u ON u.id = l.owner_id
            LEFT JOIN favorites fav ON fav.listing_id = l.id AND fav.user_id = ?::uuid
            WHERE l.id = ?::uuid AND l.status != 'DELETED'
        """.trimIndent()

        var detail: ListingDetailDto? = null

        jdbcConn.prepareStatement(coreSql).use { stmt ->
            stmt.setString(1, userId)   // favorites LEFT JOIN user_id
            stmt.setString(2, id)       // listing id (WHERE)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    val owner = OwnerDto(
                        id = rs.getString("owner_id"),
                        fullName = rs.getString("owner_name"),
                        phone = rs.getString("owner_phone"),
                        isVerified = rs.getBoolean("owner_verified")
                    )

                    // 2. Fetch related collections — each one a small targeted query
                    val rooms = fetchRooms(jdbcConn, id)
                    val photos = fetchPhotos(jdbcConn, id)
                    val amenities = fetchAmenities(jdbcConn, id)
                    val reviews = fetchRecentReviews(jdbcConn, id, limit = 5)

                    detail = ListingDetailDto(
                        id = rs.getString("id"),
                        title = rs.getString("title"),
                        description = rs.getString("description"),
                        addressLine = rs.getString("address_line"),
                        locality = rs.getString("locality"),
                        city = rs.getString("city"),
                        pincode = rs.getString("pincode"),
                        latitude = rs.getDouble("lat"),
                        longitude = rs.getDouble("lng"),
                        genderPreference = rs.getString("gender_preference") ?: "COED",
                        pgType = rs.getString("pg_type") ?: "PG",
                        foodType = rs.getString("food_type") ?: "BOTH",
                        avgRating = rs.getDouble("avg_rating"),
                        reviewCount = rs.getInt("review_count"),
                        status = rs.getString("status"),
                        owner = owner,
                        roomOptions = rooms,
                        photos = photos,
                        amenities = amenities,
                        recentReviews = reviews,
                        isFavorite = rs.getBoolean("is_favorite")
                    )
                }
            }
        }

        detail
    }

    private fun fetchRooms(conn: Connection, listingId: String): List<RoomOptionDto> {
        val sql = """
            SELECT id, sharing_type, monthly_rent, security_deposit,
                   total_beds, available_beds, notice_period_days
            FROM room_options
            WHERE listing_id = ?::uuid
            ORDER BY monthly_rent ASC
        """.trimIndent()
        val out = mutableListOf<RoomOptionDto>()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, listingId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    out.add(RoomOptionDto(
                        id = rs.getString("id"),
                        sharingType = rs.getString("sharing_type") ?: "DOUBLE",
                        monthlyRent = rs.getInt("monthly_rent"),
                        securityDeposit = rs.getInt("security_deposit"),
                        totalBeds = rs.getInt("total_beds"),
                        availableBeds = rs.getInt("available_beds"),
                        noticePeriodDays = rs.getInt("notice_period_days")
                    ))
                }
            }
        }
        return out
    }

    private fun fetchPhotos(conn: Connection, listingId: String): List<PhotoDto> {
        val sql = """
            SELECT id, url, thumbnail_url, display_order
            FROM listing_photos
            WHERE listing_id = ?::uuid
            ORDER BY display_order ASC
        """.trimIndent()
        val out = mutableListOf<PhotoDto>()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, listingId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    out.add(PhotoDto(
                        id = rs.getString("id"),
                        url = rs.getString("url"),
                        thumbnailUrl = rs.getString("thumbnail_url"),
                        displayOrder = rs.getInt("display_order")
                    ))
                }
            }
        }
        return out
    }

    private fun fetchAmenities(conn: Connection, listingId: String): List<AmenityDto> {
        val sql = """
            SELECT a.id, a.name, a.icon_key
            FROM listing_amenities la
            INNER JOIN amenities a ON a.id = la.amenity_id
            WHERE la.listing_id = ?::uuid
            ORDER BY a.name ASC
        """.trimIndent()
        val out = mutableListOf<AmenityDto>()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, listingId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    out.add(AmenityDto(
                        id = rs.getInt("id"),
                        name = rs.getString("name"),
                        iconKey = rs.getString("icon_key")
                    ))
                }
            }
        }
        return out
    }

    private fun fetchRecentReviews(conn: Connection, listingId: String, limit: Int): List<ReviewDto> {
        val sql = """
            SELECT r.id, r.user_id::text AS user_id, u.full_name AS user_name, r.rating, r.comment,
                   r.stayed_from, r.stayed_until, r.created_at
            FROM reviews r
            INNER JOIN users u ON u.id = r.user_id
            WHERE r.listing_id = ?::uuid AND r.is_flagged = FALSE
            ORDER BY r.created_at DESC
            LIMIT ?
        """.trimIndent()
        val out = mutableListOf<ReviewDto>()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, listingId)
            stmt.setInt(2, limit)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    out.add(ReviewDto(
                        id = rs.getString("id"),
                        userId = rs.getString("user_id"),
                        userName = rs.getString("user_name"),
                        rating = rs.getInt("rating"),
                        comment = rs.getString("comment"),
                        stayedFrom = rs.getString("stayed_from"),
                        stayedUntil = rs.getString("stayed_until"),
                        createdAt = rs.getString("created_at")
                    ))
                }
            }
        }
        return out
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
        distanceMeters = getDouble("dist"),
        isFavorite = getBoolean("is_favorite")
    )
}