package com.example.plateocr.data.model.gov

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Generic CKAN API response for resources with unknown/dynamic schemas.
 * Returns raw JSON objects that can be parsed based on the specific resource.
 */
@JsonClass(generateAdapter = true)
data class GenericGovApiResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "result") val result: GenericGovApiResult?
)

@JsonClass(generateAdapter = true)
data class GenericGovApiResult(
    @Json(name = "records") val records: List<Map<String, Any?>>
)
