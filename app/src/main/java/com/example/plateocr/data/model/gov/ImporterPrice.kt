package com.example.plateocr.data.model.gov

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Importer's price list
 * Resource: 39f455bf-6db0-4926-859d-017f34eacbcb
 */
@JsonClass(generateAdapter = true)
data class ImporterPrice(
    // Store ALL raw fields from API for complete display
    @Transient
    val rawFields: Map<String, Any?> = emptyMap(),

    @Json(name = "tozeret_nm")
    val manufacturer: String? = null,

    @Json(name = "kinuy_mishari")
    val model: String? = null,

    @Json(name = "shnat_yitzur")
    val year: Int? = null,

    @Json(name = "mehir_mevukash")
    val requestedPrice: Double? = null,

    @Json(name = "mehir_merhav")
    val priceRange: String? = null,

    @Json(name = "ramat_gimur")
    val trimLevel: String? = null
) {
    fun getDisplayText(): String {
        return buildString {
            requestedPrice?.let { appendLine("מחיר מבוקש: ₪${String.format("%,.0f", it)}") }
            priceRange?.let { appendLine("טווח מחירים: $it") }
            trimLevel?.let { appendLine("רמת גימור: $it") }
        }.trim()
    }
}
