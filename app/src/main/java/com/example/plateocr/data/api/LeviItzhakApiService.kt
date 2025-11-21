package com.example.plateocr.data.api

import com.example.plateocr.data.model.leviitzhak.LeviItzhakPriceRequest
import com.example.plateocr.data.model.leviitzhak.LeviItzhakPriceResponse
import com.example.plateocr.data.model.leviitzhak.LeviItzhakRequest
import com.example.plateocr.data.model.leviitzhak.LeviItzhakResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit API service for Levi Itzhak vehicle pricing API
 * Provides market price estimates, VIN lookup, and additional vehicle details
 *
 * API Documentation: https://s.leviitzhak.xyz
 */
interface LeviItzhakApiService {

    /**
     * Look up vehicle by license plate number
     *
     * Endpoint: POST /main/new-get-by-licence-plate/
     *
     * @param request Request body containing plate number and search options
     * @param token JWT authentication token
     * @return Vehicle data including VIN, price estimate, and ownership history
     */
    @POST("main/new-get-by-licence-plate/")
    suspend fun lookupPlate(
        @Body request: LeviItzhakRequest,
        @Header("Authorization") token: String
    ): Response<LeviItzhakResponse>

    /**
     * Get price estimation for a vehicle
     *
     * Endpoint: POST /main/get-price/
     *
     * @param request Request body containing vehicle kod, year, km, owners, etc.
     * @param token JWT authentication token
     * @return Price estimation with and without VAT
     */
    @POST("main/get-price/")
    suspend fun getPrice(
        @Body request: LeviItzhakPriceRequest,
        @Header("Authorization") token: String
    ): Response<LeviItzhakPriceResponse>

    companion object {
        const val BASE_URL = "https://s.leviitzhak.xyz/"

        /**
         * Format token for Authorization header
         */
        fun formatToken(token: String): String {
            return if (token.startsWith("Bearer ")) {
                token
            } else {
                "Bearer $token"
            }
        }
    }
}
