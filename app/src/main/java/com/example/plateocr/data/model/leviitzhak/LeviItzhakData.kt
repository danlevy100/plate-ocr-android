package com.example.plateocr.data.model.leviitzhak

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Request body for Levi Itzhak plate lookup API
 */
@JsonClass(generateAdapter = true)
data class LeviItzhakRequest(
    @Json(name = "plate")
    val plate: Long,

    @Json(name = "isMotorcycleSearch")
    val isMotorcycleSearch: Boolean = false
)

/**
 * Request body for Levi Itzhak price estimation API
 */
@JsonClass(generateAdapter = true)
data class LeviItzhakPriceRequest(
    @Json(name = "kod")
    val kod: String,

    @Json(name = "year")
    val year: String,

    @Json(name = "isau")
    val isau: String = "1",

    @Json(name = "owners")
    val owners: Int,

    @Json(name = "km")
    val km: String,

    @Json(name = "aliya")
    val aliya: String,

    @Json(name = "addons")
    val addons: String = "",

    @Json(name = "newOld")
    val newOld: String = "0",

    @Json(name = "percent")
    val percent: String = "100",

    @Json(name = "ownersArr")
    val ownersArr: List<String> = emptyList(),

    @Json(name = "propertyId")
    val propertyId: String = "",

    @Json(name = "props")
    val props: List<String> = emptyList(),

    @Json(name = "truckWeight")
    val truckWeight: String = "0",

    @Json(name = "siyurTiyur")
    val siyurTiyur: Int = 0,

    @Json(name = "refinement")
    val refinement: List<String> = emptyList(),

    @Json(name = "tradingCompanies")
    val tradingCompanies: String? = null,

    @Json(name = "publicTransport")
    val publicTransport: String? = null
)

/**
 * Response from Levi Itzhak price estimation API
 */
@JsonClass(generateAdapter = true)
data class LeviItzhakPriceResponse(
    @Json(name = "status")
    val status: Int? = null,

    // msg can be a string or an object depending on the response
    // Using Any? to handle both cases
    @Json(name = "msg")
    val message: Any? = null,

    @Json(name = "data")
    val data: LeviItzhakPriceData? = null
)

@JsonClass(generateAdapter = true)
data class LeviItzhakPriceData(
    @Json(name = "LeviCarPrice")
    val leviCarPrice: LeviCarPrice? = null
)

@JsonClass(generateAdapter = true)
data class LeviCarPrice(
    @Json(name = "Price")
    val price: List<Int?>? = null,

    @Json(name = "PriceNoVat")
    val priceNoVat: List<Int?>? = null
) {
    /**
     * Get the estimated price (with VAT)
     */
    fun getPrice(): Int? = price?.firstOrNull()

    /**
     * Get the estimated price without VAT
     */
    fun getPriceNoVat(): Int? = priceNoVat?.firstOrNull()
}

/**
 * Response from Levi Itzhak plate lookup API
 * Provides additional vehicle data including VIN, market price estimates, and detailed ownership info
 */
@JsonClass(generateAdapter = true)
data class LeviItzhakResponse(
    @Json(name = "status")
    val status: Int? = null,

    @Json(name = "msg")
    val message: String? = null,

    @Json(name = "data")
    val data: LeviItzhakResponseData? = null
)

@JsonClass(generateAdapter = true)
data class LeviItzhakResponseData(
    @Json(name = "search")
    val search: LeviItzhakSearchData? = null,

    @Json(name = "car")
    val car: LeviItzhakCarData? = null
)

@JsonClass(generateAdapter = true)
data class LeviItzhakSearchData(
    @Json(name = "plate")
    val plate: Long? = null,

    @Json(name = "aliya")
    val aliya: List<String?>? = null,

    @Json(name = "subModel")
    val subModel: LeviItzhakSubModel? = null
)

@JsonClass(generateAdapter = true)
data class LeviItzhakSubModel(
    @Json(name = "id")
    val id: String? = null
)

/**
 * Main vehicle data from Levi Itzhak
 */
@JsonClass(generateAdapter = true)
data class LeviItzhakCarData(
    @Json(name = "id")
    val id: String? = null,

    @Json(name = "manufacturer")
    val manufacturerId: String? = null,

    @Json(name = "manufacturerName")
    val manufacturerName: String? = null,

    @Json(name = "name")
    val name: String? = null,

    @Json(name = "year")
    val year: List<String?>? = null,

    @Json(name = "ramatGimur")
    val trimLevel: List<String?>? = null,

    @Json(name = "engineVolume")
    val engineVolume: Int? = null,

    @Json(name = "weight")
    val weight: Int? = null,

    @Json(name = "price")
    val estimatedPrice: Int? = null,

    @Json(name = "singlePrice")
    val singlePrice: Int? = null,

    @Json(name = "kmInfo")
    val kmInfo: LeviItzhakKmInfo? = null,

    @Json(name = "ownershipHistory")
    val ownershipHistory: List<LeviItzhakOwnership>? = null,

    @Json(name = "plate")
    val plate: Long? = null,

    @Json(name = "aliyaDate")
    val aliyaDate: String? = null
) {
    /**
     * Get VIN number from kmInfo
     */
    fun getVin(): String? = kmInfo?.vin

    /**
     * Get color from kmInfo
     */
    fun getColor(): String? = kmInfo?.color

    /**
     * Get last test odometer reading
     */
    fun getLastTestKm(): Int? = kmInfo?.lastTestKm

    /**
     * Get current owner type
     */
    fun getCurrentOwnerType(): String? = kmInfo?.currentOwner

    /**
     * Get market price estimate
     */
    fun getMarketPrice(): Int? = estimatedPrice ?: singlePrice

    /**
     * Get formatted trim level
     */
    fun getTrimLevelString(): String? = trimLevel?.firstOrNull()
}

/**
 * Odometer and additional info from last vehicle test
 */
@JsonClass(generateAdapter = true)
data class LeviItzhakKmInfo(
    // Can be Int or String (e.g., "בי"ס לנהיגה" for driving schools)
    @Json(name = "original")
    val original: Any? = null,

    @Json(name = "last_test_km")
    val lastTestKm: Int? = null,

    @Json(name = "color")
    val color: String? = null,

    @Json(name = "vin")
    val vin: String? = null,

    @Json(name = "current_owner")
    val currentOwner: String? = null
)

/**
 * Ownership history entry from Levi Itzhak
 * Note: Date format is MM/YYYY (e.g., "11/2018")
 */
@JsonClass(generateAdapter = true)
data class LeviItzhakOwnership(
    @Json(name = "date")
    val date: String? = null,  // MM/YYYY format

    @Json(name = "type")
    val type: String? = null  // פרטי, מסחרי, etc.
) {
    /**
     * Format date to YYYY-MM for consistency with government data
     */
    fun getFormattedDate(): String? {
        return date?.let {
            try {
                val parts = it.split("/")
                if (parts.size == 2) {
                    val month = parts[0].padStart(2, '0')
                    val year = parts[1]
                    "$year-$month"
                } else {
                    it
                }
            } catch (_: Exception) {
                it
            }
        }
    }
}
