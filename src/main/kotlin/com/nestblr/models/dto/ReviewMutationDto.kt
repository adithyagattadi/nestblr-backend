package com.nestblr.models.dto

import kotlinx.serialization.Serializable

/**
 * Body for POST /api/v1/listings/{listingId}/reviews.
 * Both fields required; bounds (rating 1..5, comment non-blank ≤1000) are
 * enforced in the route, on top of the DB-level CHECK on rating.
 */
@Serializable
data class CreateReviewRequest(
    val rating: Int,
    val comment: String
)
