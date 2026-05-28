package com.nestblr.models.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val role: String,          // "TENANT" or "OWNER"
    val fullName: String? = null,
    val phone: String? = null
)

@Serializable
data class UserDto(
    val id: String,
    val firebaseUid: String,
    val email: String?,
    val phone: String?,
    val fullName: String?,
    val role: String,
    val isVerified: Boolean
)