package com.example.plateocr.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Vehicle information from Israeli government database (data.gov.il).
 *
 * Maps to the response from resource: 053cea08-09bc-40ec-8f7a-156f0677aff3
 * API: https://data.gov.il/api/3/action/datastore_search
 */
@JsonClass(generateAdapter = true)
data class VehicleInfo(
    @Json(name = "mispar_rechev")
    val licensePlate: Long,

    // Store ALL raw fields from API for complete display
    @Transient
    val rawFields: Map<String, Any?> = emptyMap(),

    // Manufacturer and model
    @Json(name = "tozeret_nm")
    val manufacturer: String? = null,

    @Json(name = "tozeret_cd")
    val manufacturerCode: Int? = null,

    @Json(name = "kinuy_mishari")
    val model: String? = null,

    @Json(name = "degem_nm")
    val modelCode: String? = null,

    @Json(name = "degem_cd")
    val modelCodeNumber: Int? = null,

    @Json(name = "degem_manoa")
    val engineModel: String? = null,

    @Json(name = "sug_degem")
    val vehicleType: String? = null,

    // Year and registration
    @Json(name = "shnat_yitzur")
    val year: Int? = null,

    @Json(name = "moed_aliya_lakvish")
    val firstRegistration: String? = null,

    @Json(name = "horaat_rishum")
    val registrationInstruction: Int? = null,

    // Color and appearance
    @Json(name = "tzeva_rechev")
    val color: String? = null,

    @Json(name = "tzeva_cd")
    val colorCode: Int? = null,

    // Fuel and engine
    @Json(name = "sug_delek_nm")
    val fuelType: String? = null,

    // Tires
    @Json(name = "zmig_kidmi")
    val frontTireSize: String? = null,

    @Json(name = "zmig_ahori")
    val rearTireSize: String? = null,

    // Trim and features
    @Json(name = "ramat_gimur")
    val trimLevel: String? = null,

    @Json(name = "ramat_eivzur_betihuty")
    val safetyEquipmentLevel: Int? = null,

    // Environmental
    @Json(name = "kvutzat_zihum")
    val pollutionGroup: Int? = null,

    // Ownership
    @Json(name = "baalut")
    val ownerType: String? = null,

    // VIN
    @Json(name = "misgeret")
    val vin: String? = null,

    // Testing and validity
    @Json(name = "mivchan_acharon_dt")
    val lastTestDate: String? = null,

    @Json(name = "tokef_dt")
    val registrationExpiry: String? = null
) {
    /**
     * Returns true if vehicle data is valid.
     */
    fun isValid(): Boolean {
        return manufacturer != null
    }

    /**
     * Returns formatted display string for vehicle.
     */
    fun getDisplayName(): String {
        // Remove country from manufacturer name
        // Examples: "שברולט ארה"ב" -> "שברולט", "סוזוקי-יפן" -> "סוזוקי", "ב מ וו בריטניה" -> "ב מ וו"
        val manufacturerName = manufacturer?.let { name ->
            // Data-driven list extracted from data.gov.il database (50k record sample)
            // Sorted by length (longest first) to match full country names before abbreviations
            val countries = listOf(
                "אוסטריה", "בריטניה", "ד.קוריא", "הונגריה", "סלובניה", "סלובקיה", "פורטוגל",
                "אוסטרי", "איטליה", "אנגליה", "גרמניה", "טורקיה", "מכסיקו", "מקסיקו",
                "קוריאה", "רומניה", "שוודיה", "תאילנד", "תורכיה",
                "ארה״ב", "בלגיה", "ד.קור", "מרוקו", "סלובק", "סרביה", "פולין", "צ'כיה",
                "שודיה", "תאילנ",
                "אוסט", "ארהב", "בלגי", "גרמנ", "דרום", "הודו", "הונג", "מכסי", "סלוב",
                "ספרד", "פולי", "פורט", "צרפת", "קנדה", "יפן", "סין", "אפ", "גר", "ספ"
            )

            // Try to remove country suffix (space or dash separated)
            var cleaned = name
            for (country in countries) {
                cleaned = cleaned
                    .removeSuffix(" $country")
                    .removeSuffix("-$country")
            }
            cleaned
        }

        return when {
            manufacturerName != null && model != null && year != null ->
                "$manufacturerName $model ($year)"
            manufacturerName != null && model != null ->
                "$manufacturerName $model"
            manufacturerName != null ->
                manufacturerName
            else ->
                licensePlate.toString()
        }
    }

    /**
     * Returns formatted details for display.
     */
    fun getDetails(): String {
        return buildString {
            // === BASIC INFO ===
            if (!vehicleType.isNullOrEmpty()) {
                appendLine("סוג רכב: $vehicleType")
            }
            if (!trimLevel.isNullOrEmpty()) {
                appendLine("רמת גימור: $trimLevel")
            }

            // === PRICING ===
            registrationInstruction?.let { price ->
                val formattedPrice = String.format("%,d", price)
                appendLine("מחיר מקורי: ₪$formattedPrice")
            }

            // === APPEARANCE ===
            if (!color.isNullOrEmpty()) {
                appendLine("צבע: $color")
            }

            // === ENGINE & FUEL ===
            if (!fuelType.isNullOrEmpty()) {
                appendLine("סוג דלק: $fuelType")
            }
            if (!engineModel.isNullOrEmpty()) {
                appendLine("דגם מנוע: $engineModel")
            }

            // === OWNERSHIP & REGISTRATION ===
            if (!ownerType.isNullOrEmpty()) {
                appendLine("בעלות: $ownerType")
            }
            if (!firstRegistration.isNullOrEmpty()) {
                appendLine("מועד עליה לכביש: $firstRegistration")
            }

            // === TESTING & VALIDITY ===
            if (!lastTestDate.isNullOrEmpty()) {
                appendLine("מבחן אחרון: $lastTestDate")
            }
            if (!registrationExpiry.isNullOrEmpty()) {
                appendLine("רישוי עד: $registrationExpiry")
            }

            // === TIRES ===
            if (!frontTireSize.isNullOrEmpty() || !rearTireSize.isNullOrEmpty()) {
                if (frontTireSize == rearTireSize) {
                    appendLine("צמיגים: $frontTireSize")
                } else {
                    if (!frontTireSize.isNullOrEmpty()) {
                        appendLine("צמיגים קדמיים: $frontTireSize")
                    }
                    if (!rearTireSize.isNullOrEmpty()) {
                        appendLine("צמיגים אחוריים: $rearTireSize")
                    }
                }
            }

            // === ENVIRONMENTAL ===
            pollutionGroup?.let {
                appendLine("קבוצת זיהום: $it")
            }

            // === SAFETY ===
            safetyEquipmentLevel?.let {
                appendLine("רמת איבזור בטיחותי: $it")
            }

            // === TECHNICAL DETAILS ===
            if (!modelCode.isNullOrEmpty()) {
                appendLine("קוד דגם: $modelCode")
            }
            if (!vin.isNullOrEmpty()) {
                appendLine("מספר שלדה: $vin")
            }
        }.trim()
    }
}
