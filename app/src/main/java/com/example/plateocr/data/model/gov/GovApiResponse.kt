package com.example.plateocr.data.model.gov

import com.example.plateocr.data.model.VehicleInfo
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response wrapper for data.gov.il CKAN API.
 */
@JsonClass(generateAdapter = true)
data class GovApiResponse(
    @Json(name = "success")
    val success: Boolean,

    @Json(name = "result")
    val result: GovApiResult?
)

@JsonClass(generateAdapter = true)
data class GovApiResult(
    @Json(name = "records")
    val records: List<VehicleInfo>
)
