package com.example.plateocr.data.model.gov

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Vehicle recall information
 * Resource: 36bf1404-0be4-49d2-82dc-2f1ead4a8b93
 */
@JsonClass(generateAdapter = true)
data class VehicleRecall(
    @Json(name = "tozeret_nm")
    val manufacturer: String? = null,

    @Json(name = "degem_nm")
    val model: String? = null,

    @Json(name = "shnat_yitzur")
    val year: Int? = null,

    @Json(name = "teur_hazmana_hazara")
    val recallDescription: String? = null,

    @Json(name = "tarich_hotzaat_hazmana")
    val recallDate: String? = null,

    @Json(name = "status_hazmana")
    val recallStatus: String? = null,

    @Json(name = "mispar_hazmana")
    val recallNumber: String? = null
) {
    fun getDisplayText(): String {
        return buildString {
            recallDate?.let { appendLine("תאריך: $it") }
            recallDescription?.let { appendLine("תיאור: $it") }
            recallStatus?.let { appendLine("סטטוס: $it") }
        }.trim()
    }
}
