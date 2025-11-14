package com.example.plateocr.data.model.gov

/**
 * Disabled parking permit (תו נכה) information.
 *
 * Data source: Resource ID c8b9f9c8-4612-4068-934f-d4acd2e3c06e
 */
data class DisabledTag(
    val rawFields: Map<String, Any?>,
    val licensePlate: Long?,
    val tagType: String?,  // Type of disabled permit
    val expiryDate: String?,
    val issueDate: String?
)
