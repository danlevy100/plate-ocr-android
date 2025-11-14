package com.example.plateocr.data.api

import com.example.plateocr.data.model.gov.GovApiResponse
import com.example.plateocr.data.model.gov.GenericGovApiResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit API service for Israeli government vehicle database.
 *
 * Interfaces directly with data.gov.il CKAN API
 * to retrieve vehicle information by license plate number.
 *
 * API Documentation: https://data.gov.il/dataset/vehicle-information
 * Resource ID: 053cea08-09bc-40ec-8f7a-156f0677aff3 (license plate lookup)
 */
interface VehicleApiService {

    /**
     * Looks up vehicle by license plate from gov database.
     *
     * Example query:
     * https://data.gov.il/api/3/action/datastore_search?
     *   resource_id=053cea08-09bc-40ec-8f7a-156f0677aff3&
     *   filters={"mispar_rechev":"45774301"}&
     *   limit=1
     *
     * @param resourceId The CKAN resource ID (default: license plate resource)
     * @param filters JSON filter string (e.g., {"mispar_rechev":"45774301"})
     * @param q Full-text search query (alternative to filters, used for recalls)
     * @param limit Maximum number of results (default: 1)
     * @return API response containing vehicle records
     */
    @GET("datastore_search")
    suspend fun lookupVehicle(
        @Query("resource_id") resourceId: String = LICENSE_PLATE_RESOURCE_ID,
        @Query("filters") filters: String? = null,
        @Query("q") q: String? = null,
        @Query("limit") limit: Int = 1
    ): Response<GovApiResponse>

    /**
     * Generic lookup for resources with dynamic schemas.
     * Returns raw JSON maps that can be manually parsed.
     */
    @GET("datastore_search")
    suspend fun lookupGeneric(
        @Query("resource_id") resourceId: String,
        @Query("filters") filters: String? = null,
        @Query("q") q: String? = null,
        @Query("limit") limit: Int = 1
    ): Response<GenericGovApiResponse>

    companion object {
        // Israeli government open data API
        const val BASE_URL = "https://data.gov.il/api/3/action/"

        // CKAN resource IDs for different data sources
        const val LICENSE_PLATE_RESOURCE_ID = "053cea08-09bc-40ec-8f7a-156f0677aff3"  // Basic vehicle info
        const val OWNERSHIP_HISTORY_RESOURCE_ID = "bb2355dc-9ec7-4f06-9c3f-3344672171da"  // Ownership history
        const val WLTP_SPECS_RESOURCE_ID = "142afde2-6228-49f9-8a29-9b6c3a0cbe40"  // WLTP emissions & specs
        const val RECALLS_RESOURCE_ID = "36bf1404-0be4-49d2-82dc-2f1ead4a8b93"  // Vehicle recalls
        const val KM_FLAGS_RESOURCE_ID = "56063a99-8a3e-4ff4-912e-5966c0279bad"  // Odometer & flags
        const val IMPORTER_PRICE_RESOURCE_ID = "39f455bf-6db0-4926-859d-017f34eacbcb"  // Importer price list
        const val MODEL_MONTHLY_COUNTS_RESOURCE_ID = "602ac32d-19c0-4b41-88e0-e3ce8a7e80b7"  // Monthly registration counts by model
        const val DISABLED_TAG_RESOURCE_ID = "c8b9f9c8-4612-4068-934f-d4acd2e3c06e"  // Disabled parking permit
        const val EU_TYPE_A_RESOURCE_ID = "7cb2bd95-bf2e-49b6-aea1-fcb5ff6f0473"  // EU Type Approval (A)
        const val EU_TYPE_B_RESOURCE_ID = "786b33b5-75c4-42a3-a241-b1af3c9ca487"  // EU Type Approval (B)
        const val PLATE_UPDATES_RESOURCE_ID = "83bfb278-7be1-4dab-ae2d-40125a923da1"  // Plate update pings
        const val VIN_WMI_MIRROR_RESOURCE_ID = "03adc637-b6fe-402b-9937-7c3d3afc9140"  // VIN/WMI mirror data
    }
}
