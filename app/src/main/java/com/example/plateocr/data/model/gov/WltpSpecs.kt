package com.example.plateocr.data.model.gov

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * WLTP emissions and technical specifications
 * Resource: 142afde2-6228-49f9-8a29-9b6c3a0cbe40
 */
@JsonClass(generateAdapter = true)
data class WltpSpecs(
    // Store ALL raw fields from API for complete display
    @Transient
    val rawFields: Map<String, Any?> = emptyMap(),

    @Json(name = "tozeret_cd")
    val manufacturerCode: Int? = null,

    @Json(name = "degem_cd")
    val modelCode: Int? = null,

    @Json(name = "shnat_yitzur")
    val year: Int? = null,

    // Emissions
    @Json(name = "CO2_WLTP")
    val co2Wltp: Double? = null,

    @Json(name = "HC_WLTP")
    val hcWltp: Double? = null,

    @Json(name = "PM_WLTP")
    val pmWltp: Double? = null,

    @Json(name = "NOX_WLTP")
    val noxWltp: Double? = null,

    @Json(name = "CO_WLTP")
    val coWltp: Double? = null,

    @Json(name = "CO2_WLTP_NEDC")
    val co2WltpNedc: Double? = null,

    // Fuel consumption
    @Json(name = "tzrihat_delek_manoa_bakir")
    val fuelConsumptionCold: Double? = null,

    @Json(name = "tzrihat_delek_ironit")
    val fuelConsumptionCity: Double? = null,

    @Json(name = "tzrihat_delek_kvish_huz")
    val fuelConsumptionHighway: Double? = null,

    @Json(name = "tzrihat_delek_meshuleshet")
    val fuelConsumptionCombined: Double? = null,

    // Electric range
    @Json(name = "tevah_hashmalit_wltp")
    val electricRangeWltp: Double? = null,

    @Json(name = "tevah_hashmalit_ironit_wltp")
    val electricRangeCityWltp: Double? = null
) {
    fun getDisplayText(): String {
        return buildString {
            co2Wltp?.let { appendLine("פליטת CO2: ${String.format("%.1f", it)} g/km") }
            fuelConsumptionCombined?.let { appendLine("צריכת דלק: ${String.format("%.1f", it)} L/100km") }
            fuelConsumptionCity?.let { appendLine("צריכה עירונית: ${String.format("%.1f", it)} L/100km") }
            fuelConsumptionHighway?.let { appendLine("צריכה בכביש מהיר: ${String.format("%.1f", it)} L/100km") }
            electricRangeWltp?.let { appendLine("טווח חשמלי: ${String.format("%.0f", it)} km") }
            noxWltp?.let { appendLine("פליטת NOx: ${String.format("%.3f", it)} g/km") }
        }.trim()
    }
}
