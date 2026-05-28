package com.nestblr.repositories

import com.nestblr.config.DatabaseFactory.dbQuery
import com.nestblr.models.dto.CreateListingRequest
import com.nestblr.models.dto.OwnerListingDto
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.sql.Connection
import java.sql.Statement

class OwnerListingRepository {

    /**
     * Creates a listing owned by [ownerId], plus its room options and amenity links,
     * all in one transaction. Returns the new listing's id.
     *
     * Uses raw SQL because of the PostGIS GEOGRAPHY point construction
     * (ST_MakePoint(lng, lat)) which Exposed's DSL doesn't model cleanly.
     */
    suspend fun createListing(ownerId: String, req: CreateListingRequest): String = dbQuery {
        val conn: Connection = TransactionManager.current().connection.connection as Connection

        // 1. Insert the listing, building the geography point from lat/lng
        val listingId: String
        val insertListing = """
            INSERT INTO listings
                (owner_id, title, description, address_line, locality, city, pincode,
                 contact_phone, location, gender_preference, pg_type, food_type, status)
            VALUES
                (?::uuid, ?, ?, ?, ?, ?, ?,
                 ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?, 'ACTIVE')
            RETURNING id
        """.trimIndent()

        conn.prepareStatement(insertListing).use { stmt ->
            stmt.setString(1, ownerId)
            stmt.setString(2, req.title)
            stmt.setString(3, req.description)
            stmt.setString(4, req.addressLine)
            stmt.setString(5, req.locality)
            stmt.setString(6, req.city)
            stmt.setString(7, req.pincode)
            stmt.setString(8, req.contactPhone)
            stmt.setDouble(9, req.longitude)   // ST_MakePoint takes (lng, lat)
            stmt.setDouble(10, req.latitude)
            stmt.setString(11, req.genderPreference)
            stmt.setString(12, req.pgType)
            stmt.setString(13, req.foodType)
            stmt.executeQuery().use { rs ->
                rs.next()
                listingId = rs.getString("id")
            }
        }

        // 2. Insert room options
        if (req.roomOptions.isNotEmpty()) {
            val insertRoom = """
                INSERT INTO room_options
                    (listing_id, sharing_type, monthly_rent, security_deposit,
                     total_beds, available_beds, notice_period_days)
                VALUES (?::uuid, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            conn.prepareStatement(insertRoom).use { stmt ->
                for (room in req.roomOptions) {
                    stmt.setString(1, listingId)
                    stmt.setString(2, room.sharingType)
                    stmt.setInt(3, room.monthlyRent)
                    stmt.setInt(4, room.securityDeposit)
                    stmt.setInt(5, room.totalBeds)
                    stmt.setInt(6, room.availableBeds)
                    stmt.setInt(7, room.noticePeriodDays)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }

        // 3. Link amenities
        if (req.amenityIds.isNotEmpty()) {
            val insertAmenity = """
                INSERT INTO listing_amenities (listing_id, amenity_id)
                VALUES (?::uuid, ?)
                ON CONFLICT DO NOTHING
            """.trimIndent()
            conn.prepareStatement(insertAmenity).use { stmt ->
                for (amenityId in req.amenityIds) {
                    stmt.setString(1, listingId)
                    stmt.setInt(2, amenityId)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }

        listingId
    }

    /** Returns the owner_id of a listing, or null if it doesn't exist. */
    suspend fun getOwnerId(listingId: String): String? = dbQuery {
        val conn: Connection = TransactionManager.current().connection.connection as Connection
        conn.prepareStatement("SELECT owner_id FROM listings WHERE id = ?::uuid").use { stmt ->
            stmt.setString(1, listingId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getString("owner_id") else null
            }
        }
    }

    /** Updates core listing fields. Caller must verify ownership first. */
    suspend fun updateListing(listingId: String, req: CreateListingRequest) = dbQuery {
        val conn: Connection = TransactionManager.current().connection.connection as Connection
        val sql = """
            UPDATE listings SET
                title = ?, description = ?, address_line = ?, locality = ?,
                city = ?, pincode = ?, contact_phone = ?,
                location = ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                gender_preference = ?, pg_type = ?, food_type = ?,
                updated_at = NOW()
            WHERE id = ?::uuid
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, req.title)
            stmt.setString(2, req.description)
            stmt.setString(3, req.addressLine)
            stmt.setString(4, req.locality)
            stmt.setString(5, req.city)
            stmt.setString(6, req.pincode)
            stmt.setString(7, req.contactPhone)
            stmt.setDouble(8, req.longitude)
            stmt.setDouble(9, req.latitude)
            stmt.setString(10, req.genderPreference)
            stmt.setString(11, req.pgType)
            stmt.setString(12, req.foodType)
            stmt.setString(13, listingId)
            stmt.executeUpdate()
        }
    }

    /** Soft-deletes by setting status = DELETED. Caller must verify ownership. */
    suspend fun softDeleteListing(listingId: String) = dbQuery {
        val conn: Connection = TransactionManager.current().connection.connection as Connection
        conn.prepareStatement("UPDATE listings SET status = 'DELETED', updated_at = NOW() WHERE id = ?::uuid").use { stmt ->
            stmt.setString(1, listingId)
            stmt.executeUpdate()
        }
    }

    /** Lists all non-deleted listings owned by [ownerId]. */
    suspend fun listByOwner(ownerId: String): List<OwnerListingDto> = dbQuery {
        val conn: Connection = TransactionManager.current().connection.connection as Connection
        val sql = """
            SELECT
                l.id, l.title, l.locality, l.pg_type, l.gender_preference,
                l.status, l.avg_rating, l.review_count,
                MIN(r.monthly_rent) AS min_rent,
                COUNT(r.id) AS room_count
            FROM listings l
            LEFT JOIN room_options r ON r.listing_id = l.id
            WHERE l.owner_id = ?::uuid AND l.status <> 'DELETED'
            GROUP BY l.id
            ORDER BY l.created_at DESC
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, ownerId)
            stmt.executeQuery().use { rs ->
                val out = mutableListOf<OwnerListingDto>()
                while (rs.next()) {
                    val minRent = rs.getInt("min_rent").let { if (rs.wasNull()) null else it }
                    out.add(
                        OwnerListingDto(
                            id = rs.getString("id"),
                            title = rs.getString("title"),
                            locality = rs.getString("locality"),
                            pgType = rs.getString("pg_type"),
                            genderPreference = rs.getString("gender_preference"),
                            status = rs.getString("status"),
                            avgRating = rs.getBigDecimal("avg_rating")?.toDouble() ?: 0.0,
                            reviewCount = rs.getInt("review_count"),
                            minRent = minRent,
                            roomCount = rs.getInt("room_count")
                        )
                    )
                }
                out
            }
        }
    }
}