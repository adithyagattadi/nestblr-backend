package com.nestblr.models.dto

import kotlinx.serialization.Serializable

/**
 * Payload for creating or editing a listing.
 * Location comes as plain lat/lng — backend builds the GEOGRAPHY point.
 * (Temporary: Android sends approximate coords from a locality dropdown.
 *  Later this is replaced by a map pin picker — backend is unchanged.)
 */
@Serializable
data class CreateListingRequest(
    val title: String,
    val description: String? = null,
    val addressLine: String,
    val locality: String,
    val city: String = "Bengaluru",
    val pincode: String? = null,
    val latitude: Double,
    val longitude: Double,
    val genderPreference: String,   // MALE | FEMALE | COED
    val pgType: String,             // PG | HOSTEL | COLIVING
    val foodType: String,           // VEG | NON_VEG | BOTH | NONE
    val roomOptions: List<CreateRoomOptionRequest> = emptyList(),
    val amenityIds: List<Int> = emptyList()
)

@Serializable
data class CreateRoomOptionRequest(
    val sharingType: String,        // SINGLE | DOUBLE | TRIPLE | QUAD
    val monthlyRent: Int,
    val securityDeposit: Int,
    val totalBeds: Int,
    val availableBeds: Int,
    val noticePeriodDays: Int = 30
)

/** Minimal listing summary for the owner's own list view. */
@Serializable
data class OwnerListingDto(
    val id: String,
    val title: String,
    val locality: String,
    val pgType: String,
    val genderPreference: String,
    val status: String,
    val avgRating: Double,
    val reviewCount: Int,
    val minRent: Int?,
    val roomCount: Int
)