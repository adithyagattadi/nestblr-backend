package com.nestblr.models.dto

import kotlinx.serialization.Serializable

@Serializable
data class ListingDetailDto(
    val id: String,
    val title: String,
    val description: String?,
    val addressLine: String,
    val locality: String,
    val city: String,
    val pincode: String?,
    val latitude: Double,
    val longitude: Double,
    val genderPreference: String,
    val pgType: String,
    val foodType: String,
    val avgRating: Double,
    val reviewCount: Int,
    val status: String,
    val owner: OwnerDto,
    val roomOptions: List<RoomOptionDto>,
    val photos: List<PhotoDto>,
    val amenities: List<AmenityDto>,
    val recentReviews: List<ReviewDto>,
    // Whether the calling (authenticated) user has favorited this listing.
    val isFavorite: Boolean = false
)

@Serializable
data class OwnerDto(
    val id: String,
    val fullName: String?,
    val phone: String,
    val isVerified: Boolean
)

@Serializable
data class RoomOptionDto(
    val id: String,
    val sharingType: String,
    val monthlyRent: Int,
    val securityDeposit: Int,
    val totalBeds: Int,
    val availableBeds: Int,
    val noticePeriodDays: Int
)

@Serializable
data class PhotoDto(
    val id: String,
    val url: String,
    val thumbnailUrl: String,
    val displayOrder: Int
)

@Serializable
data class AmenityDto(
    val id: Int,
    val name: String,
    val iconKey: String?
)

@Serializable
data class ReviewDto(
    val id: String,
    val userId: String,
    val userName: String?,
    val rating: Int,
    val comment: String?,
    val stayedFrom: String?,
    val stayedUntil: String?,
    val createdAt: String
)