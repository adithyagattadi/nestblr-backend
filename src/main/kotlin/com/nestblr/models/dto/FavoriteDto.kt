package com.nestblr.models.dto

import kotlinx.serialization.Serializable

/**
 * A single favorites row returned by POST /api/v1/me/favorites/{listingId}.
 * createdAt is the raw timestamptz string, matching how other DTOs surface
 * timestamps (see ReviewDto.createdAt) — no custom Instant serializer needed.
 */
@Serializable
data class FavoriteDto(
    val listingId: String,
    val createdAt: String
)
