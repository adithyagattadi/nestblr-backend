package com.nestblr.models.dto

import kotlinx.serialization.Serializable

/**
 * A single inquiry row, returned to the tenant who logged it.
 * createdAt = first attempt, lastAttemptAt = most recent attempt (bumped on repeat).
 * Timestamps are raw timestamptz strings, matching the other DTOs (e.g. ReviewDto).
 */
@Serializable
data class InquiryDto(
    val listingId: String,
    val tenantId: String,
    val createdAt: String,
    val lastAttemptAt: String
)

/**
 * Owner-facing, aggregated-by-listing view. Carries NO tenant identity —
 * only how many distinct tenants inquired and when the latest one was.
 */
@Serializable
data class InquirySummaryDto(
    val listingId: String,
    val listingTitle: String,
    val uniqueInquirerCount: Int,
    val lastInquiryAt: String
)
