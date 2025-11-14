package com.example.plateocr.data.model.gov

/**
 * Monthly registration counts for a specific vehicle model.
 * Shows how many vehicles of this model were registered each month.
 *
 * Data source: Resource ID 602ac32d-19c0-4b41-88e0-e3ce8a7e80b7
 */
data class ModelMonthlyCounts(
    val rawFields: Map<String, Any?>,
    val sgiraMonth: String?,  // YYYYMM format
    val carNum: Int?,  // Number of cars registered in that month
    val manufacturerCode: Int?,
    val modelCode: String?
)
