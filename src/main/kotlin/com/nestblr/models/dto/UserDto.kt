package com.nestblr.models.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val role: String,          // "TENANT" or "OWNER"
    val fullName: String? = null,
    val phone: String? = null,
    val gender: String? = null,    // NEW — "MALE" | "FEMALE" | "OTHER" | "PREFER_NOT_TO_SAY"
    val dob: String? = null        // NEW — ISO date string "YYYY-MM-DD" (e.g. "2000-05-15")
)

@Serializable
data class UserDto(
    val id: String,
    val firebaseUid: String,
    val email: String?,
    val phone: String?,
    val fullName: String?,
    val role: String,
    val isVerified: Boolean,
    val gender: String? = null,    // NEW
    val dob: String? = null        // NEW — serialized as ISO date string
)