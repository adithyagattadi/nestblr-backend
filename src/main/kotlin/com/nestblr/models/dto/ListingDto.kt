package com.nestblr.models.dto

import kotlinx.serialization.Serializable

@Serializable
data class ListingSummaryDto(
    val id: String,
    val title: String,
    val locality: String,
    val addressLine: String,
    val latitude: Double,
    val longitude: Double,
    val genderPreference: String,
    val pgType: String,
    val foodType: String,
    val avgRating: Double,
    val reviewCount: Int,
    val minRent: Int?,
    val coverPhotoUrl: String?,
    val distanceMeters: Double? = null,
    // Whether the calling (authenticated) user has favorited this listing.
    // Defaults false so any non-authenticated deserialization still works.
    val isFavorite: Boolean = false
)

@Serializable
data class PageMeta(
    val page: Int,
    val size: Int,
    val total: Int
)

@Serializable
data class ApiResponse<T>(
    val data: T,
    val error: String? = null,
    val meta: PageMeta? = null
)

@Serializable
data class SearchFilters(
    val lat: Double,
    val lng: Double,
    val radiusKm: Double = 5.0,
    val minRent: Int? = null,
    val maxRent: Int? = null,
    val gender: String? = null,
    val food: String? = null,
    val pgType: String? = null,
    val page: Int = 0,
    val size: Int = 20
)
