package com.example.plateocr.data.model.gov

/**
 * Plate update ping/event information.
 * Tracks when vehicle registration was updated in the system.
 *
 * Data source: Resource ID 83bfb278-7be1-4dab-ae2d-40125a923da1
 */
data class PlateUpdate(
    val rawFields: Map<String, Any?>,
    val licensePlate: Long?,
    val updateDate: String?,
    val updateType: String?
)
