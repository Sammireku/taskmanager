package com.example.places

data class TaskPlaceInfo(
    val placeId: String,
    val name: String,
    val address: String? = null,
    val phoneNumber: String? = null,
    val websiteUri: String? = null,
    val rating: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)
