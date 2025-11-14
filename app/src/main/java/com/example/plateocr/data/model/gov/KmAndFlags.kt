package com.example.plateocr.data.model.gov

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Odometer readings and vehicle flags
 * Resource: 56063a99-8a3e-4ff4-912e-5966c0279bad
 */
@JsonClass(generateAdapter = true)
data class KmAndFlags(
    @Json(name = "mispar_rechev")
    val licensePlate: Long,

    // Store ALL raw fields from API for complete display
    @Transient
    val rawFields: Map<String, Any?> = emptyMap(),

    @Json(name = "km")
    val odometer: Long? = null,

    @Json(name = "taarich_rechashat_km")
    val odometerDate: String? = null,

    @Json(name = "ramat_eivzur_betihuty")
    val safetyEquipmentLevel: Int? = null,

    @Json(name = "kvutzat_zihum")
    val pollutionGroup: Int? = null,

    @Json(name = "mkoriut_nm")
    val ownershipOrigin: String? = null  // פרטי, השכרה, ליסינג, etc.
) {
    fun getDisplayText(lastTestDate: String? = null): String {
        return buildString {
            odometer?.let {
                if (lastTestDate != null) {
                    appendLine("מד מרחק: ${String.format("%,d", it)} ק״מ (טסט אחרון: $lastTestDate)")
                } else {
                    appendLine("מד מרחק: ${String.format("%,d", it)} ק״מ")
                }
            }
            odometerDate?.let { appendLine("תאריך רישום ראשון: $it") }
            ownershipOrigin?.let { appendLine("מקור: $it") }
            safetyEquipmentLevel?.let { appendLine("רמת ביטחון: $it") }
            pollutionGroup?.let { appendLine("קבוצת זיהום: $it") }
        }.trim()
    }
}
