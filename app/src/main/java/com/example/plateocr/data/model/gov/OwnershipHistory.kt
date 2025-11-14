package com.example.plateocr.data.model.gov

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Ownership history record from government database
 * Resource: bb2355dc-9ec7-4f06-9c3f-3344672171da
 *
 * Shows ownership changes over time with date (YYYYMM format) and type
 */
@JsonClass(generateAdapter = true)
data class OwnershipHistory(
    @Json(name = "mispar_rechev")
    val licensePlate: Long,

    @Json(name = "baalut_dt")
    val ownershipDate: Int? = null,  // YYYYMM format (e.g., 202404 = April 2024)

    @Json(name = "baalut")
    val ownershipType: String? = null  // פרטי, סוחר, החכר, etc.
) {
    /**
     * Format ownership date from YYYYMM to YYYY-MM format
     */
    fun getFormattedDate(): String? {
        return ownershipDate?.let {
            val year = it / 100
            val month = it % 100
            String.format("%04d-%02d", year, month)
        }
    }
}
