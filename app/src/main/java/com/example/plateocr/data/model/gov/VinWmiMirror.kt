package com.example.plateocr.data.model.gov

/**
 * VIN/WMI (World Manufacturer Identifier) mirror data.
 * Additional technical identification information.
 *
 * Data source: Resource ID 03adc637-b6fe-402b-9937-7c3d3afc9140
 */
data class VinWmiMirror(
    val rawFields: Map<String, Any?>,
    val licensePlate: Long?,
    val vin: String?,
    val wmi: String?,  // World Manufacturer Identifier (first 3 chars of VIN)
    val manufacturer: String?
)
