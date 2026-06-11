package com.nestblr.models.dto

import kotlinx.serialization.Serializable

/**
 * Body for PATCH /api/v1/owner/listings/{listingId}/rooms/{roomId}.
 * Laser-scoped: only the absolute target available-beds count. Everything
 * else about a room (rent, deposit, total beds, sharing type) goes through
 * the full PUT-replaces-all-rooms flow.
 */
@Serializable
data class UpdateRoomAvailabilityRequest(val availableBeds: Int)
