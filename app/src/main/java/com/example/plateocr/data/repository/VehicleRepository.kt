package com.example.plateocr.data.repository

import android.util.Log
import com.example.plateocr.data.api.ApiClient
import com.example.plateocr.data.api.LeviItzhakApiService
import com.example.plateocr.data.api.VehicleApiService
import com.example.plateocr.data.model.VehicleInfo
import com.example.plateocr.data.model.gov.*
import com.example.plateocr.data.model.leviitzhak.LeviCarPrice
import com.example.plateocr.data.model.leviitzhak.LeviItzhakCarData
import com.example.plateocr.data.model.leviitzhak.LeviItzhakPriceRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Repository for querying Israeli government vehicle database.
 *
 * Supports progressive loading - emits updates as each data source loads.
 */
class VehicleRepository {

    private val api = ApiClient.vehicleApiService

    // Levi Itzhak API client
    private val leviItzhakApi: LeviItzhakApiService by lazy {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                Log.d(TAG, "Levi Itzhak Request: ${request.method} ${request.url}")
                Log.d(TAG, "Headers: ${request.headers}")
                try {
                    val response = chain.proceed(request)
                    Log.d(TAG, "Levi Itzhak Response: ${response.code}")
                    if (!response.isSuccessful) {
                        val bodyString = response.peekBody(2048).string()
                        Log.e(TAG, "Levi Itzhak Error Response: $bodyString")
                    }
                    response
                } catch (e: Exception) {
                    Log.e(TAG, "Levi Itzhak Network Error", e)
                    throw e
                }
            }
            .build()

        Retrofit.Builder()
            .baseUrl(LeviItzhakApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LeviItzhakApiService::class.java)
    }

    /**
     * Simple lookup for basic vehicle info only (for backward compatibility).
     *
     * @param plateNumber License plate number (7-8 digits, dashes optional)
     * @return Result with VehicleInfo if found, null otherwise
     */
    suspend fun lookupByPlate(plateNumber: String): Result<VehicleInfo?> {
        return try {
            val cleanPlate = plateNumber.replace("-", "").replace(" ", "")
            val vehicle = fetchBasicVehicleInfo(cleanPlate)
            Result.success(vehicle)
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up vehicle by plate", e)
            Result.failure(e)
        }
    }

    /**
     * Progressively loads comprehensive vehicle data from multiple government sources.
     * Emits AggregateVehicleData updates as each API call completes.
     * API calls are parallelized for faster loading.
     *
     * @param plateNumber License plate number (7-8 digits, dashes optional)
     * @return Flow of AggregateVehicleData with progressive updates
     */
    fun lookupComprehensiveData(plateNumber: String): Flow<AggregateVehicleData> = channelFlow {
        val cleanPlate = plateNumber.replace("-", "").replace(" ", "")
        var data = AggregateVehicleData()
        val mutex = Mutex()

        Log.d(TAG, "Starting comprehensive lookup for plate: $cleanPlate (parallel mode)")

        // Step 1: Load basic vehicle info (REQUIRED - must succeed)
        try {
            val vehicle = fetchBasicVehicleInfo(cleanPlate)
            if (vehicle != null) {
                data = data.copy(vehicle = vehicle)
                send(data)
                Log.d(TAG, "✓ Basic info loaded: ${vehicle.getDisplayName()}")
            } else {
                Log.e(TAG, "✗ Vehicle not found")
                send(data)
                return@channelFlow  // Stop if vehicle not found
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to load basic info", e)
            send(data)
            return@channelFlow
        }

        // Get manufacturer/model codes for subsequent queries
        val vehicle = data.vehicle!!
        val tozeretCd = vehicle.manufacturerCode
        val degemCd = vehicle.modelCodeNumber
        val year = vehicle.year
        val degemNm = vehicle.modelCode

        // Set all loading states to true
        data = data.copy(
            isLoadingKmAndFlags = true,
            isLoadingOwnershipHistory = true,
            isLoadingWltpSpecs = tozeretCd != null && degemCd != null,
            isLoadingImporterPrice = tozeretCd != null && degemCd != null,
            isLoadingRecalls = true,
            isLoadingModelCounts = tozeretCd != null && degemCd != null,
            isLoadingDisabledTag = true,
            isLoadingEuType = true,
            isLoadingPlateUpdates = true,
            isLoadingVinWmi = true,
            isLoadingLeviItzhak = true,
            isLoadingLeviItzhakPrice = true
        )
        send(data)

        // Variables to collect data for step 13 (price estimate)
        var leviItzhakKod: String? = null
        var leviItzhakAliya: String? = null
        var leviItzhakYear: String? = null

        // Launch all API calls in parallel
        coroutineScope {
            // Plate-only calls (Group 1)

            // KM & flags
            val kmJob = async {
                try {
                    val kmFlags = fetchKmAndFlags(cleanPlate)
                    mutex.withLock {
                        data = data.copy(kmAndFlags = kmFlags, isLoadingKmAndFlags = false)
                    }
                    if (kmFlags != null) {
                        Log.d(TAG, "✓ KM & flags loaded (odometer: ${kmFlags.odometer})")
                    } else {
                        Log.w(TAG, "✗ KM & flags returned null (no records found)")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "✗ Failed to load KM & flags", e)
                    mutex.withLock {
                        data = data.copy(isLoadingKmAndFlags = false)
                    }
                }
                send(data)
            }

            // Ownership history
            val ownershipJob = async {
                try {
                    val history = fetchOwnershipHistory(cleanPlate)
                    mutex.withLock {
                        data = data.copy(ownershipHistory = history, isLoadingOwnershipHistory = false)
                    }
                    Log.d(TAG, "✓ Ownership history loaded (${history.size} records)")
                } catch (e: Exception) {
                    Log.w(TAG, "✗ Failed to load ownership history", e)
                    mutex.withLock {
                        data = data.copy(isLoadingOwnershipHistory = false)
                    }
                }
                send(data)
            }

            // Recalls
            async {
                try {
                    val recalls = fetchRecalls(cleanPlate)
                    mutex.withLock {
                        data = data.copy(recalls = recalls, isLoadingRecalls = false)
                    }
                    send(data)
                    Log.d(TAG, "✓ Recalls loaded (${recalls.size} records)")
                } catch (e: Exception) {
                    Log.w(TAG, "✗ Failed to load recalls", e)
                    mutex.withLock {
                        data = data.copy(isLoadingRecalls = false)
                    }
                    send(data)
                }
            }

            // Disabled tag
            async {
                try {
                    val disabledTag = fetchDisabledTag(cleanPlate)
                    mutex.withLock {
                        data = data.copy(disabledTag = disabledTag, isLoadingDisabledTag = false)
                    }
                    send(data)
                    Log.d(TAG, "✓ Disabled tag loaded")
                } catch (e: Exception) {
                    Log.w(TAG, "✗ Failed to load disabled tag", e)
                    mutex.withLock {
                        data = data.copy(isLoadingDisabledTag = false)
                    }
                    send(data)
                }
            }

            // EU type approval
            async {
                try {
                    val euTypeA = fetchEuTypeApproval(cleanPlate, VehicleApiService.EU_TYPE_A_RESOURCE_ID)
                    val euTypeB = fetchEuTypeApproval(cleanPlate, VehicleApiService.EU_TYPE_B_RESOURCE_ID)
                    mutex.withLock {
                        data = data.copy(
                            euTypeApprovalA = euTypeA,
                            euTypeApprovalB = euTypeB,
                            isLoadingEuType = false
                        )
                    }
                    send(data)
                    Log.d(TAG, "✓ EU type approval loaded (A: ${euTypeA.size}, B: ${euTypeB.size})")
                } catch (e: Exception) {
                    Log.w(TAG, "✗ Failed to load EU type approval", e)
                    mutex.withLock {
                        data = data.copy(isLoadingEuType = false)
                    }
                    send(data)
                }
            }

            // Plate updates
            async {
                try {
                    val updates = fetchPlateUpdates(cleanPlate)
                    mutex.withLock {
                        data = data.copy(plateUpdates = updates, isLoadingPlateUpdates = false)
                    }
                    send(data)
                    Log.d(TAG, "✓ Plate updates loaded (${updates.size} records)")
                } catch (e: Exception) {
                    Log.w(TAG, "✗ Failed to load plate updates", e)
                    mutex.withLock {
                        data = data.copy(isLoadingPlateUpdates = false)
                    }
                    send(data)
                }
            }

            // VIN/WMI mirror
            async {
                try {
                    val vinWmi = fetchVinWmiMirror(cleanPlate)
                    mutex.withLock {
                        data = data.copy(vinWmiMirror = vinWmi, isLoadingVinWmi = false)
                    }
                    send(data)
                    Log.d(TAG, "✓ VIN/WMI mirror loaded")
                } catch (e: Exception) {
                    Log.w(TAG, "✗ Failed to load VIN/WMI mirror", e)
                    mutex.withLock {
                        data = data.copy(isLoadingVinWmi = false)
                    }
                    send(data)
                }
            }

            // Levi Itzhak data
            val leviItzhakJob = async {
                try {
                    val (leviItzhakData, kod, aliya, yearFromApi) = fetchLeviItzhakDataWithKod(cleanPlate)
                    mutex.withLock {
                        leviItzhakKod = kod
                        leviItzhakAliya = aliya
                        leviItzhakYear = yearFromApi
                        data = data.copy(leviItzhak = leviItzhakData, isLoadingLeviItzhak = false)
                    }
                    send(data)
                    Log.d(TAG, "✓ Levi Itzhak data loaded (VIN: ${leviItzhakData?.getVin()}, Price: ${leviItzhakData?.getMarketPrice()}, kod: $kod, year: $yearFromApi, aliya: $aliya, ownershipHistory: ${leviItzhakData?.ownershipHistory?.size ?: 0} entries)")
                } catch (e: Exception) {
                    Log.w(TAG, "✗ Failed to load Levi Itzhak data", e)
                    mutex.withLock {
                        data = data.copy(isLoadingLeviItzhak = false)
                    }
                    send(data)
                }
            }

            // Code-dependent calls (Group 2)
            if (tozeretCd != null && degemCd != null) {
                // WLTP specs
                async {
                    try {
                        val wltp = fetchWltpSpecs(tozeretCd, degemCd, degemNm, year)
                        mutex.withLock {
                            data = data.copy(wltpSpecs = wltp, isLoadingWltpSpecs = false)
                        }
                        send(data)
                        Log.d(TAG, "✓ WLTP specs loaded")
                    } catch (e: Exception) {
                        Log.w(TAG, "✗ Failed to load WLTP specs", e)
                        mutex.withLock {
                            data = data.copy(isLoadingWltpSpecs = false)
                        }
                        send(data)
                    }
                }

                // Importer price
                async {
                    try {
                        val price = fetchImporterPrice(tozeretCd, degemCd, degemNm, year)
                        mutex.withLock {
                            data = data.copy(importerPrice = price, isLoadingImporterPrice = false)
                        }
                        send(data)
                        Log.d(TAG, "✓ Importer price loaded")
                    } catch (e: Exception) {
                        Log.w(TAG, "✗ Failed to load importer price", e)
                        mutex.withLock {
                            data = data.copy(isLoadingImporterPrice = false)
                        }
                        send(data)
                    }
                }

                // Model monthly counts
                async {
                    try {
                        val counts = fetchModelMonthlyCounts(tozeretCd, degemCd, degemNm)
                        mutex.withLock {
                            data = data.copy(modelMonthlyCounts = counts, isLoadingModelCounts = false)
                        }
                        send(data)
                        val total = counts.sumOf { it.carNum ?: 0 }
                        Log.d(TAG, "✓ Model monthly counts loaded (${counts.size} months, $total total vehicles)")
                    } catch (e: Exception) {
                        Log.w(TAG, "✗ Failed to load model monthly counts", e)
                        mutex.withLock {
                            data = data.copy(isLoadingModelCounts = false)
                        }
                        send(data)
                    }
                }
            } else {
                Log.w(TAG, "Skipping WLTP/price/counts - missing codes")
                mutex.withLock {
                    data = data.copy(
                        isLoadingWltpSpecs = false,
                        isLoadingImporterPrice = false,
                        isLoadingModelCounts = false
                    )
                }
                send(data)
            }

            // Wait for dependencies needed for price estimate (km, ownership, levi itzhak)
            kmJob.await()
            ownershipJob.await()
            leviItzhakJob.await()
        }

        // Step 13: Load Levi Itzhak price estimate (with km and owners from gov API)
        val km = data.kmAndFlags?.odometer
        // Count only actual owners, excluding dealers (סוחר)
        val ownersCount = data.ownershipHistory?.count { it.ownershipType != "סוחר" } ?: 1
        val validKm = if (km != null && km > 0) km else null

        if (leviItzhakKod != null && leviItzhakYear != null && validKm != null) {
            try {
                // Build ownersArr for display
                val ownersArr = buildOwnersArr(data.ownershipHistory)

                val priceEstimate = fetchLeviItzhakPriceEstimate(
                    kod = leviItzhakKod!!,
                    year = leviItzhakYear!!,
                    km = validKm,
                    owners = ownersCount.coerceAtLeast(1),
                    aliyaDate = leviItzhakAliya,
                    ownershipHistory = data.ownershipHistory
                )
                data = data.copy(
                    leviItzhakPrice = priceEstimate,
                    leviItzhakOwnersArr = ownersArr,
                    isLoadingLeviItzhakPrice = false
                )
                send(data)
                Log.d(TAG, "✓ Levi Itzhak price estimate loaded (Price: ${priceEstimate?.getPrice()}, km: $validKm, owners: $ownersCount, ownersArr: $ownersArr)")
            } catch (e: Exception) {
                Log.w(TAG, "✗ Failed to load Levi Itzhak price estimate", e)
                data = data.copy(isLoadingLeviItzhakPrice = false)
                send(data)
            }
        } else {
            val skipReason = when {
                leviItzhakKod == null -> "missing kod"
                leviItzhakYear == null -> "missing year"
                km == null -> "missing km"
                km == 0L -> "km is 0 (invalid)"
                else -> "unknown"
            }
            Log.w(TAG, "Skipping Levi Itzhak price estimate - $skipReason")
            data = data.copy(isLoadingLeviItzhakPrice = false)
            send(data)
        }

        Log.d(TAG, "Comprehensive lookup complete - ${data.getLoadedSectionsCount()}/13 sections loaded")
    }

    private suspend fun fetchBasicVehicleInfo(cleanPlate: String): VehicleInfo? {
        val filterJson = JSONObject().apply {
            put("mispar_rechev", cleanPlate)
        }.toString()

        val response = api.lookupGeneric(
            resourceId = VehicleApiService.LICENSE_PLATE_RESOURCE_ID,
            filters = filterJson
        )

        return if (response.isSuccessful && response.body()?.success == true) {
            val records = response.body()?.result?.records
            if (!records.isNullOrEmpty()) {
                parseVehicleInfo(records[0])
            } else {
                null
            }
        } else {
            null
        }
    }

    private fun parseVehicleInfo(record: Map<String, Any?>): VehicleInfo? {
        return try {
            val licensePlate = when (val v = record["mispar_rechev"]) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull() ?: return null
                else -> return null
            }

            VehicleInfo(
                licensePlate = licensePlate,
                rawFields = record, // Store ALL raw fields
                manufacturer = record["tozeret_nm"] as? String,
                manufacturerCode = (record["tozeret_cd"] as? Number)?.toInt(),
                model = record["kinuy_mishari"] as? String,
                modelCode = record["degem_nm"] as? String,
                modelCodeNumber = (record["degem_cd"] as? Number)?.toInt(),
                engineModel = record["degem_manoa"] as? String,
                vehicleType = record["sug_degem"] as? String,
                year = (record["shnat_yitzur"] as? Number)?.toInt(),
                firstRegistration = record["moed_aliya_lakvish"] as? String,
                registrationInstruction = (record["horaat_rishum"] as? Number)?.toInt(),
                color = record["tzeva_rechev"] as? String,
                colorCode = (record["tzeva_cd"] as? Number)?.toInt(),
                fuelType = record["sug_delek_nm"] as? String,
                frontTireSize = record["zmig_kidmi"] as? String,
                rearTireSize = record["zmig_ahori"] as? String,
                trimLevel = record["ramat_gimur"] as? String,
                safetyEquipmentLevel = (record["ramat_eivzur_betihuty"] as? Number)?.toInt(),
                pollutionGroup = (record["kvutzat_zihum"] as? Number)?.toInt(),
                ownerType = record["baalut"] as? String,
                vin = record["misgeret"] as? String,
                lastTestDate = record["mivchan_acharon_dt"] as? String,
                registrationExpiry = record["tokef_dt"] as? String
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse vehicle info", e)
            null
        }
    }

    private suspend fun fetchKmAndFlags(cleanPlate: String): KmAndFlags? {
        val filterJson = JSONObject().apply {
            put("mispar_rechev", cleanPlate)
        }.toString()

        val response = api.lookupGeneric(
            resourceId = VehicleApiService.KM_FLAGS_RESOURCE_ID,
            filters = filterJson,
            limit = 1
        )

        if (!response.isSuccessful) {
            Log.w(TAG, "KM & flags API HTTP error: ${response.code()} ${response.message()}")
            return null
        }

        return if (response.body()?.success == true) {
            val records = response.body()?.result?.records
            Log.d(TAG, "KM & flags API response: ${records?.size ?: 0} records")
            if (!records.isNullOrEmpty()) {
                val parsed = parseKmAndFlags(records[0])
                Log.d(TAG, "KM & flags parsed: odometer=${parsed?.odometer}, raw kilometer_test_aharon=${records[0]["kilometer_test_aharon"]}")
                parsed
            } else {
                null
            }
        } else {
            Log.w(TAG, "KM & flags API returned success=false")
            null
        }
    }

    private fun parseKmAndFlags(record: Map<String, Any?>): KmAndFlags? {
        return try {
            val licensePlate = when (val v = record["mispar_rechev"]) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull() ?: return null
                else -> return null
            }

            // API returns "kilometer_test_aharon" not "km"
            val odometer = when (val v = record["kilometer_test_aharon"]) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull()
                else -> null
            }

            // Get the first registration date
            val odometerDate = record["rishum_rishon_dt"] as? String

            // Get ownership origin (השכרה, פרטי, ליסינג, etc.)
            val ownershipOrigin = record["mkoriut_nm"] as? String

            KmAndFlags(
                licensePlate = licensePlate,
                rawFields = record, // Store ALL raw fields
                odometer = odometer,
                odometerDate = odometerDate,
                safetyEquipmentLevel = (record["ramat_eivzur_betihuty"] as? Number)?.toInt(),
                pollutionGroup = (record["kvutzat_zihum"] as? Number)?.toInt(),
                ownershipOrigin = ownershipOrigin
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse KM & flags", e)
            null
        }
    }

    private suspend fun fetchOwnershipHistory(cleanPlate: String): List<OwnershipHistory> {
        val filterJson = JSONObject().apply {
            put("mispar_rechev", cleanPlate)
        }.toString()

        val response = api.lookupGeneric(
            resourceId = VehicleApiService.OWNERSHIP_HISTORY_RESOURCE_ID,
            filters = filterJson,
            limit = 200  // Get full history
        )

        return if (response.isSuccessful && response.body()?.success == true) {
            val records = response.body()?.result?.records ?: emptyList()
            records.mapNotNull { parseOwnershipHistory(it) }
        } else {
            emptyList()
        }
    }

    private fun parseOwnershipHistory(record: Map<String, Any?>): OwnershipHistory? {
        return try {
            val licensePlate = when (val v = record["mispar_rechev"]) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull() ?: return null
                else -> return null
            }

            OwnershipHistory(
                licensePlate = licensePlate,
                ownershipDate = (record["baalut_dt"] as? Number)?.toInt(),
                ownershipType = record["baalut"] as? String
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse ownership history record", e)
            null
        }
    }

    private suspend fun fetchWltpSpecs(tozeretCd: Int, degemCd: Int, degemNm: String?, year: Int?): WltpSpecs? {
        // Try with all filters first
        val filters1 = JSONObject().apply {
            put("tozeret_cd", tozeretCd)
            put("degem_cd", degemCd)
            degemNm?.let { put("degem_nm", it) }
            year?.let { put("shnat_yitzur", it) }
        }.toString()

        var response = api.lookupGeneric(
            resourceId = VehicleApiService.WLTP_SPECS_RESOURCE_ID,
            filters = filters1,
            limit = 10
        )

        // Fallback 1: Try without year
        if (response.body()?.result?.records.isNullOrEmpty() && year != null) {
            val filters2 = JSONObject().apply {
                put("tozeret_cd", tozeretCd)
                put("degem_cd", degemCd)
                degemNm?.let { put("degem_nm", it) }
            }.toString()

            response = api.lookupGeneric(
                resourceId = VehicleApiService.WLTP_SPECS_RESOURCE_ID,
                filters = filters2,
                limit = 10
            )
        }

        // Fallback 2: Try without degem_nm
        if (response.body()?.result?.records.isNullOrEmpty() && degemNm != null) {
            val filters3 = JSONObject().apply {
                put("tozeret_cd", tozeretCd)
                put("degem_cd", degemCd)
                year?.let { put("shnat_yitzur", it) }
            }.toString()

            response = api.lookupGeneric(
                resourceId = VehicleApiService.WLTP_SPECS_RESOURCE_ID,
                filters = filters3,
                limit = 10
            )
        }

        return if (response.isSuccessful && response.body()?.success == true) {
            val records = response.body()?.result?.records
            if (!records.isNullOrEmpty()) {
                parseWltpSpecs(records[0])
            } else {
                null
            }
        } else {
            null
        }
    }

    private fun parseWltpSpecs(record: Map<String, Any?>): WltpSpecs? {
        return try {
            WltpSpecs(
                rawFields = record, // Store ALL raw fields
                manufacturerCode = (record["tozeret_cd"] as? Number)?.toInt(),
                modelCode = (record["degem_cd"] as? Number)?.toInt(),
                year = (record["shnat_yitzur"] as? Number)?.toInt(),
                co2Wltp = (record["CO2_WLTP"] as? Number)?.toDouble(),
                hcWltp = (record["HC_WLTP"] as? Number)?.toDouble(),
                pmWltp = (record["PM_WLTP"] as? Number)?.toDouble(),
                noxWltp = (record["NOX_WLTP"] as? Number)?.toDouble(),
                coWltp = (record["CO_WLTP"] as? Number)?.toDouble(),
                co2WltpNedc = (record["CO2_WLTP_NEDC"] as? Number)?.toDouble(),
                fuelConsumptionCold = (record["tzrihat_delek_manoa_bakir"] as? Number)?.toDouble(),
                fuelConsumptionCity = (record["tzrihat_delek_ironit"] as? Number)?.toDouble(),
                fuelConsumptionHighway = (record["tzrihat_delek_kvish_huz"] as? Number)?.toDouble(),
                fuelConsumptionCombined = (record["tzrihat_delek_meshuleshet"] as? Number)?.toDouble(),
                electricRangeWltp = (record["tevah_hashmalit_wltp"] as? Number)?.toDouble(),
                electricRangeCityWltp = (record["tevah_hashmalit_ironit_wltp"] as? Number)?.toDouble()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse WLTP specs", e)
            null
        }
    }

    private suspend fun fetchImporterPrice(tozeretCd: Int, degemCd: Int, degemNm: String?, year: Int?): ImporterPrice? {
        // Try with all filters first
        val filters1 = JSONObject().apply {
            put("tozeret_cd", tozeretCd)
            put("degem_cd", degemCd)
            degemNm?.let { put("degem_nm", it) }
            year?.let { put("shnat_yitzur", it) }
        }.toString()

        var response = api.lookupGeneric(
            resourceId = VehicleApiService.IMPORTER_PRICE_RESOURCE_ID,
            filters = filters1,
            limit = 10
        )

        // Fallback: Try without year
        if (response.body()?.result?.records.isNullOrEmpty() && year != null) {
            val filters2 = JSONObject().apply {
                put("tozeret_cd", tozeretCd)
                put("degem_cd", degemCd)
                degemNm?.let { put("degem_nm", it) }
            }.toString()

            response = api.lookupGeneric(
                resourceId = VehicleApiService.IMPORTER_PRICE_RESOURCE_ID,
                filters = filters2,
                limit = 10
            )
        }

        return if (response.isSuccessful && response.body()?.success == true) {
            val records = response.body()?.result?.records
            if (!records.isNullOrEmpty()) {
                parseImporterPrice(records[0])
            } else {
                null
            }
        } else {
            null
        }
    }

    private fun parseImporterPrice(record: Map<String, Any?>): ImporterPrice? {
        return try {
            val requestedPrice = (record["mehir_mevukash"] as? Number)?.toDouble()
                ?: (record["mehir"] as? Number)?.toDouble()  // Fallback field name

            ImporterPrice(
                rawFields = record, // Store ALL raw fields
                manufacturer = record["tozeret_nm"] as? String,
                model = record["kinuy_mishari"] as? String,
                year = (record["shnat_yitzur"] as? Number)?.toInt(),
                requestedPrice = requestedPrice,
                priceRange = record["mehir_merhav"] as? String,
                trimLevel = record["ramat_gimur"] as? String
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse importer price", e)
            null
        }
    }

    private suspend fun fetchRecalls(cleanPlate: String): List<VehicleRecall> {
        // Recalls use full-text search (q parameter) instead of filters
        val response = api.lookupGeneric(
            resourceId = VehicleApiService.RECALLS_RESOURCE_ID,
            q = cleanPlate,
            limit = 50
        )

        return if (response.isSuccessful && response.body()?.success == true) {
            val records = response.body()?.result?.records ?: emptyList()
            records.mapNotNull { parseRecall(it) }
        } else {
            emptyList()
        }
    }

    private fun parseRecall(record: Map<String, Any?>): VehicleRecall? {
        return try {
            VehicleRecall(
                manufacturer = record["tozeret_nm"] as? String,
                model = record["degem_nm"] as? String,
                year = (record["shnat_yitzur"] as? Number)?.toInt(),
                recallDescription = record["teur_hazmana_hazara"] as? String,
                recallDate = record["tarich_hotzaat_hazmana"] as? String,
                recallStatus = record["status_hazmana"] as? String,
                recallNumber = record["mispar_hazmana"] as? String
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse recall record", e)
            null
        }
    }

    private suspend fun fetchModelMonthlyCounts(tozeretCd: Int, degemCd: Int, degemNm: String?): List<ModelMonthlyCounts> {
        // Format degem_cd as 4-digit string with leading zeros
        val degemCdFormatted = String.format(java.util.Locale.US, "%04d", degemCd)

        val filters = JSONObject().apply {
            put("tozeret_cd", tozeretCd)
            put("degem_cd", degemCdFormatted)
            degemNm?.let { put("degem_nm", it) }
        }.toString()

        val response = api.lookupGeneric(
            resourceId = VehicleApiService.MODEL_MONTHLY_COUNTS_RESOURCE_ID,
            filters = filters,
            limit = 1200  // Get full history
        )

        return if (response.isSuccessful && response.body()?.success == true) {
            val records = response.body()?.result?.records ?: emptyList()
            records.mapNotNull { parseModelMonthlyCounts(it) }
                .sortedBy { it.sgiraMonth }  // Sort by month
        } else {
            emptyList()
        }
    }

    private fun parseModelMonthlyCounts(record: Map<String, Any?>): ModelMonthlyCounts? {
        return try {
            // sgira_month can come as String or Number from API
            val sgiraMonth = when (val v = record["sgira_month"]) {
                is String -> v
                is Number -> v.toLong().toString()  // Convert to Long first to remove decimals
                else -> null
            }

            ModelMonthlyCounts(
                rawFields = record,
                sgiraMonth = sgiraMonth,
                carNum = (record["car_num"] as? Number)?.toInt(),
                manufacturerCode = (record["tozeret_cd"] as? Number)?.toInt(),
                modelCode = record["degem_cd"] as? String
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse model monthly counts", e)
            null
        }
    }

    private suspend fun fetchDisabledTag(cleanPlate: String): DisabledTag? {
        val filterJson = JSONObject().apply {
            put("MISPAR RECHEV", cleanPlate)
        }.toString()

        val response = api.lookupGeneric(
            resourceId = VehicleApiService.DISABLED_TAG_RESOURCE_ID,
            filters = filterJson,
            limit = 1
        )

        return if (response.isSuccessful && response.body()?.success == true) {
            val records = response.body()?.result?.records
            if (!records.isNullOrEmpty()) {
                parseDisabledTag(records[0])
            } else {
                null
            }
        } else {
            null
        }
    }

    private fun parseDisabledTag(record: Map<String, Any?>): DisabledTag? {
        return try {
            val licensePlate = when (val v = record["MISPAR RECHEV"]) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull()
                else -> null
            }

            DisabledTag(
                rawFields = record,
                licensePlate = licensePlate,
                tagType = record["SUG_TOV"] as? String,
                expiryDate = record["TAARICH_SIUM"] as? String,
                issueDate = record["TAARICH_MATCHIL"] as? String
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse disabled tag", e)
            null
        }
    }

    private suspend fun fetchEuTypeApproval(cleanPlate: String, resourceId: String): List<EuTypeApproval> {
        val filterJson = JSONObject().apply {
            put("mispar_rechev", cleanPlate)
        }.toString()

        val response = api.lookupGeneric(
            resourceId = resourceId,
            filters = filterJson,
            limit = 5
        )

        return if (response.isSuccessful && response.body()?.success == true) {
            val records = response.body()?.result?.records ?: emptyList()
            records.mapNotNull { parseEuTypeApproval(it) }
        } else {
            emptyList()
        }
    }

    private fun parseEuTypeApproval(record: Map<String, Any?>): EuTypeApproval? {
        return try {
            val licensePlate = when (val v = record["mispar_rechev"]) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull()
                else -> null
            }

            EuTypeApproval(
                rawFields = record,
                licensePlate = licensePlate,
                typeApprovalNumber = record["type_approval_number"] as? String,
                variant = record["variant"] as? String,
                version = record["version"] as? String
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse EU type approval", e)
            null
        }
    }

    private suspend fun fetchPlateUpdates(cleanPlate: String): List<PlateUpdate> {
        val filterJson = JSONObject().apply {
            put("mispar_rechev", cleanPlate)
        }.toString()

        val response = api.lookupGeneric(
            resourceId = VehicleApiService.PLATE_UPDATES_RESOURCE_ID,
            filters = filterJson,
            limit = 5
        )

        return if (response.isSuccessful && response.body()?.success == true) {
            val records = response.body()?.result?.records ?: emptyList()
            records.mapNotNull { parsePlateUpdate(it) }
        } else {
            emptyList()
        }
    }

    private fun parsePlateUpdate(record: Map<String, Any?>): PlateUpdate? {
        return try {
            val licensePlate = when (val v = record["mispar_rechev"]) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull()
                else -> null
            }

            PlateUpdate(
                rawFields = record,
                licensePlate = licensePlate,
                updateDate = record["update_date"] as? String,
                updateType = record["update_type"] as? String
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse plate update", e)
            null
        }
    }

    private suspend fun fetchVinWmiMirror(cleanPlate: String): VinWmiMirror? {
        val filterJson = JSONObject().apply {
            put("mispar_rechev", cleanPlate)
        }.toString()

        val response = api.lookupGeneric(
            resourceId = VehicleApiService.VIN_WMI_MIRROR_RESOURCE_ID,
            filters = filterJson,
            limit = 1
        )

        return if (response.isSuccessful && response.body()?.success == true) {
            val records = response.body()?.result?.records
            if (!records.isNullOrEmpty()) {
                parseVinWmiMirror(records[0])
            } else {
                null
            }
        } else {
            null
        }
    }

    private fun parseVinWmiMirror(record: Map<String, Any?>): VinWmiMirror? {
        return try {
            val licensePlate = when (val v = record["mispar_rechev"]) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull()
                else -> null
            }

            VinWmiMirror(
                rawFields = record,
                licensePlate = licensePlate,
                vin = record["vin"] as? String,
                wmi = record["wmi"] as? String,
                manufacturer = record["manufacturer"] as? String
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse VIN/WMI mirror", e)
            null
        }
    }

    /**
     * Data class to hold Levi Itzhak response data needed for price estimation
     */
    private data class LeviItzhakExtractedData(
        val car: LeviItzhakCarData?,
        val kod: String?,
        val aliya: String?,
        val year: String?
    )

    /**
     * Fetch vehicle data from Levi Itzhak API
     * Returns the car data, kod, aliya, and year for price estimation
     * kod is extracted from car.id or search.subModel.id
     * year is extracted from car.year[0] or search.aliya[0]
     * aliya is extracted from search.aliya[0] in format "YYYY-MM"
     */
    private suspend fun fetchLeviItzhakDataWithKod(cleanPlate: String): LeviItzhakExtractedData {
        return try {
            val token = LEVI_ITZHAK_TOKEN
            val authHeader = LeviItzhakApiService.formatToken(token)

            // Convert plate to Long (API expects integer)
            val plateAsLong = cleanPlate.toLongOrNull()
            if (plateAsLong == null) {
                Log.w(TAG, "Failed to convert plate to Long: $cleanPlate")
                return LeviItzhakExtractedData(null, null, null, null)
            }

            val request = com.example.plateocr.data.model.leviitzhak.LeviItzhakRequest(
                plate = plateAsLong,
                isMotorcycleSearch = false
            )

            val response = leviItzhakApi.lookupPlate(
                request = request,
                token = authHeader
            )

            if (response.isSuccessful && response.body()?.status == 1) {
                val responseData = response.body()?.data
                val car = responseData?.car
                val search = responseData?.search

                // Get kod from car.id or fallback to search.subModel.id
                val kod = car?.id ?: search?.subModel?.id

                // Get year from car.year[0] or extract from search.aliya[0]
                var year: String? = car?.year?.firstOrNull()
                if (year == null && !search?.aliya.isNullOrEmpty()) {
                    val aliyaPart = search.aliya.firstOrNull()
                    if (aliyaPart != null && aliyaPart.contains("-")) {
                        year = aliyaPart.split("-")[0]
                    }
                }

                // Get aliya from search.aliya[0] (format: "YYYY-MM")
                val aliya = search?.aliya?.firstOrNull()

                Log.d(TAG, "Levi Itzhak extracted: kod=$kod, year=$year, aliya=$aliya")
                LeviItzhakExtractedData(car, kod, aliya, year)
            } else {
                Log.w(TAG, "Levi Itzhak API returned unsuccessful status: ${response.body()?.status}, message: ${response.body()?.message}")
                LeviItzhakExtractedData(null, null, null, null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch Levi Itzhak data", e)
            LeviItzhakExtractedData(null, null, null, null)
        }
    }

    /**
     * Fetch price estimate from Levi Itzhak API
     * Uses km and owners data from government API for accurate pricing
     */
    private suspend fun fetchLeviItzhakPriceEstimate(
        kod: String,
        year: String,
        km: Long,
        owners: Int,
        aliyaDate: String?,
        ownershipHistory: List<OwnershipHistory>?
    ): LeviCarPrice? {
        return try {
            val token = LEVI_ITZHAK_TOKEN
            val authHeader = LeviItzhakApiService.formatToken(token)

            // Format aliya date: convert from YYYY-MM-DD to 01/MM/YYYY
            val formattedAliya = formatAliyaDate(aliyaDate, year)

            // Build ownersArr from ownership history (excluding dealers)
            val ownersArr = buildOwnersArr(ownershipHistory)

            val request = LeviItzhakPriceRequest(
                kod = kod,
                year = year,
                km = km.toString(),
                owners = owners,
                aliya = formattedAliya,
                ownersArr = ownersArr
            )

            Log.d(TAG, "Fetching price estimate: kod=$kod, year=$year, km=$km, owners=$owners, aliya=$formattedAliya, ownersArr=$ownersArr")

            val response = leviItzhakApi.getPrice(
                request = request,
                token = authHeader
            )

            if (response.isSuccessful && response.body()?.status == 1) {
                response.body()?.data?.leviCarPrice
            } else {
                Log.w(TAG, "Levi Itzhak price API returned unsuccessful status: ${response.body()?.status}, message: ${response.body()?.message}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch Levi Itzhak price estimate", e)
            null
        }
    }

    /**
     * Format aliya date to 01/MM/YYYY format expected by Levi Itzhak API
     * Input can be:
     * - Levi Itzhak format: "YYYY-MM" (e.g., "2018-11")
     * - Gov API format: "YYYY-MM-DD" (e.g., "2018-11-15")
     */
    private fun formatAliyaDate(aliyaDate: String?, year: String): String {
        if (aliyaDate.isNullOrBlank()) {
            // Default to 01/01/YEAR if no aliya date
            return "01/01/$year"
        }

        return try {
            // Try to parse YYYY-MM or YYYY-MM-DD format
            val parts = aliyaDate.split("-")
            if (parts.size >= 2) {
                val yearPart = parts[0]
                val monthPart = parts[1].padStart(2, '0')
                "01/$monthPart/$yearPart"
            } else {
                "01/01/$year"
            }
        } catch (_: Exception) {
            "01/01/$year"
        }
    }

    /**
     * Build ownersArr list from ownership history for Levi Itzhak price API.
     * Maps gov DB ownership types to Levi Itzhak lineID values.
     * Format: ["lineID-2", "lineID-2-12", ...] (array of strings)
     */
    private fun buildOwnersArr(ownershipHistory: List<OwnershipHistory>?): List<String> {
        if (ownershipHistory.isNullOrEmpty()) return emptyList()

        // Filter out dealers and map to lineID strings with time suffix
        return ownershipHistory
            .filter { it.ownershipType != "סוחר" }
            .mapNotNull { entry ->
                mapOwnershipTypeToLineIdString(entry.ownershipType)
            }
    }

    /**
     * Map gov DB ownership type (Hebrew) to Levi Itzhak lineID string.
     * Includes MONUSED time suffix where applicable.
     * Based on Levi Itzhak /main/get-owners/ endpoint values.
     */
    private fun mapOwnershipTypeToLineIdString(ownershipType: String?): String? {
        if (ownershipType == null) return null

        return when {
            // Private ownership - lineID 0, MONUSED 0
            ownershipType.contains("פרטי") -> "0-2"

            // Rental company - lineID 26, MONUSED 12
            ownershipType.contains("השכרה") -> "26-2-12"

            // Company/organization - lineID 35, MONUSED 12
            ownershipType.contains("חברה") || ownershipType.contains("עמותה") -> "35-2-12"

            // Leasing (general from gov DB) - default to private operational
            // lineID 99, MONUSED 0
            ownershipType.contains("ליסינג") || ownershipType.contains("החכר") -> "99-2"

            // Default to private if unknown
            else -> "0-2"
        }
    }

    companion object {
        private const val TAG = "VehicleRepository"

        // Levi Itzhak API token (hardcoded for now - could be moved to BuildConfig or secure storage)
        private const val LEVI_ITZHAK_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJfaWQiOiI2OGFhMmQzMTRmOWQ3MTE3ZTA0YThhN2UiLCJpYXQiOjE3NTU5ODMxODN9.OXAAMiVaRtxO9EBFqHQDikg9cTwi_uyAK_H2NBmxJls"
    }
}
