package com.example.plateocr.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton object that provides configured Retrofit API clients.
 *
 * Usage:
 * ```kotlin
 * val api = ApiClient.vehicleApiService
 * val result = api.lookupVehicle("1234567")
 * ```
 */
object ApiClient {

    /**
     * Moshi instance for JSON parsing.
     * KotlinJsonAdapterFactory enables Kotlin data class support.
     */
    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    /**
     * OkHttp client with logging interceptor for debugging.
     */
    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Retrofit instance configured with Moshi converter.
     */
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(VehicleApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    /**
     * Vehicle API service instance.
     */
    val vehicleApiService: VehicleApiService by lazy {
        retrofit.create(VehicleApiService::class.java)
    }
}
