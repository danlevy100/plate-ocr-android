package com.example.plateocr.data.repository

import android.util.Log
import com.example.plateocr.data.api.ApiClient
import com.example.plateocr.data.api.VehicleApiService
import com.example.plateocr.data.model.VehicleInfo
import com.example.plateocr.data.model.gov.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject

/**
 * Repository for querying Israeli government vehicle database.
 *
 * Supports progressive loading - emits updates as each data source loads.
 */
class VehicleRepository {

    private val api = ApiClient.vehicleApiService

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
     *
     * @param plateNumber License plate number (7-8 digits, dashes optional)
     * @return Flow of AggregateVehicleData with progressive updates
     */
    fun lookupComprehensiveData(plateNumber: String): Flow<AggregateVehicleData> = flow {
        val cleanPlate = plateNumber.replace("-", "").replace(" ", "")
        var data = AggregateVehicleData()

        Log.d(TAG, "Starting comprehensive lookup for plate: $cleanPlate")

        // Step 1: Load basic vehicle info (REQUIRED - must succeed)
        try {
            val vehicle = fetchBasicVehicleInfo(cleanPlate)
            if (vehicle != null) {
                data = data.copy(vehicle = vehicle)
                emit(data)
                Log.d(TAG, "✓ Basic info loaded: ${vehicle.getDisplayName()}")
            } else {
                Log.e(TAG, "✗ Vehicle not found")
                emit(data)
                return@flow  // Stop if vehicle not found
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to load basic info", e)
            emit(data)
            return@flow
        }

        // Get manufacturer/model codes for subsequent queries
        val vehicle = data.vehicle!!
        val tozeretCd = vehicle.manufacturerCode
        val degemCd = vehicle.modelCodeNumber
        val year = vehicle.year
        val degemNm = vehicle.modelCode

        // Step 2: Load odometer & flags
        data = data.copy(isLoadingKmAndFlags = true)
        emit(data)
        try {
            val kmFlags = fetchKmAndFlags(cleanPlate)
            data = data.copy(kmAndFlags = kmFlags, isLoadingKmAndFlags = false)
            emit(data)
            Log.d(TAG, "✓ KM & flags loaded")
        } catch (e: Exception) {
            Log.w(TAG, "✗ Failed to load KM & flags", e)
            data = data.copy(isLoadingKmAndFlags = false)
            emit(data)
        }

        // Step 3: Load ownership history
        data = data.copy(isLoadingOwnershipHistory = true)
        emit(data)
        try {
            val history = fetchOwnershipHistory(cleanPlate)
            data = data.copy(ownershipHistory = history, isLoadingOwnershipHistory = false)
            emit(data)
            Log.d(TAG, "✓ Ownership history loaded (${history.size} records)")
        } catch (e: Exception) {
            Log.w(TAG, "✗ Failed to load ownership history", e)
            data = data.copy(isLoadingOwnershipHistory = false)
            emit(data)
        }

        // Step 4: Load WLTP specs (requires manufacturer/model codes)
        if (tozeretCd != null && degemCd != null) {
            data = data.copy(isLoadingWltpSpecs = true)
            emit(data)
            try {
                val wltp = fetchWltpSpecs(tozeretCd, degemCd, degemNm, year)
                data = data.copy(wltpSpecs = wltp, isLoadingWltpSpecs = false)
                emit(data)
                Log.d(TAG, "✓ WLTP specs loaded")
            } catch (e: Exception) {
                Log.w(TAG, "✗ Failed to load WLTP specs", e)
                data = data.copy(isLoadingWltpSpecs = false)
                emit(data)
            }

            // Step 5: Load importer price
            data = data.copy(isLoadingImporterPrice = true)
            emit(data)
            try {
                val price = fetchImporterPrice(tozeretCd, degemCd, degemNm, year)
                data = data.copy(importerPrice = price, isLoadingImporterPrice = false)
                emit(data)
                Log.d(TAG, "✓ Importer price loaded")
            } catch (e: Exception) {
                Log.w(TAG, "✗ Failed to load importer price", e)
                data = data.copy(isLoadingImporterPrice = false)
                emit(data)
            }
        } else {
            Log.w(TAG, "Skipping WLTP/price - missing codes")
            data = data.copy(
                isLoadingWltpSpecs = false,
                isLoadingImporterPrice = false
            )
            emit(data)
        }

        // Step 6: Load recalls (using full-text search)
        data = data.copy(isLoadingRecalls = true)
        emit(data)
        try {
            val recalls = fetchRecalls(cleanPlate)
            data = data.copy(recalls = recalls, isLoadingRecalls = false)
            emit(data)
            Log.d(TAG, "✓ Recalls loaded (${recalls.size} records)")
        } catch (e: Exception) {
            Log.w(TAG, "✗ Failed to load recalls", e)
            data = data.copy(isLoadingRecalls = false)
            emit(data)
        }

        // Step 7: Load model monthly counts (requires manufacturer/model codes)
        if (tozeretCd != null && degemCd != null) {
            data = data.copy(isLoadingModelCounts = true)
            emit(data)
            try {
                val counts = fetchModelMonthlyCounts(tozeretCd, degemCd, degemNm)
                data = data.copy(modelMonthlyCounts = counts, isLoadingModelCounts = false)
                emit(data)
                val total = counts.sumOf { it.carNum ?: 0 }
                Log.d(TAG, "✓ Model monthly counts loaded (${counts.size} months, $total total vehicles)")
            } catch (e: Exception) {
                Log.w(TAG, "✗ Failed to load model monthly counts", e)
                data = data.copy(isLoadingModelCounts = false)
                emit(data)
            }
        } else {
            Log.w(TAG, "Skipping model counts - missing codes")
            data = data.copy(isLoadingModelCounts = false)
            emit(data)
        }

        // Step 8: Load disabled tag
        data = data.copy(isLoadingDisabledTag = true)
        emit(data)
        try {
            val disabledTag = fetchDisabledTag(cleanPlate)
            data = data.copy(disabledTag = disabledTag, isLoadingDisabledTag = false)
            emit(data)
            Log.d(TAG, "✓ Disabled tag loaded")
        } catch (e: Exception) {
            Log.w(TAG, "✗ Failed to load disabled tag", e)
            data = data.copy(isLoadingDisabledTag = false)
            emit(data)
        }

        // Step 9: Load EU type approval data
        data = data.copy(isLoadingEuType = true)
        emit(data)
        try {
            val euTypeA = fetchEuTypeApproval(cleanPlate, VehicleApiService.EU_TYPE_A_RESOURCE_ID)
            val euTypeB = fetchEuTypeApproval(cleanPlate, VehicleApiService.EU_TYPE_B_RESOURCE_ID)
            data = data.copy(
                euTypeApprovalA = euTypeA,
                euTypeApprovalB = euTypeB,
                isLoadingEuType = false
            )
            emit(data)
            Log.d(TAG, "✓ EU type approval loaded (A: ${euTypeA.size}, B: ${euTypeB.size})")
        } catch (e: Exception) {
            Log.w(TAG, "✗ Failed to load EU type approval", e)
            data = data.copy(isLoadingEuType = false)
            emit(data)
        }

        // Step 10: Load plate updates
        data = data.copy(isLoadingPlateUpdates = true)
        emit(data)
        try {
            val updates = fetchPlateUpdates(cleanPlate)
            data = data.copy(plateUpdates = updates, isLoadingPlateUpdates = false)
            emit(data)
            Log.d(TAG, "✓ Plate updates loaded (${updates.size} records)")
        } catch (e: Exception) {
            Log.w(TAG, "✗ Failed to load plate updates", e)
            data = data.copy(isLoadingPlateUpdates = false)
            emit(data)
        }

        // Step 11: Load VIN/WMI mirror
        data = data.copy(isLoadingVinWmi = true)
        emit(data)
        try {
            val vinWmi = fetchVinWmiMirror(cleanPlate)
            data = data.copy(vinWmiMirror = vinWmi, isLoadingVinWmi = false)
            emit(data)
            Log.d(TAG, "✓ VIN/WMI mirror loaded")
        } catch (e: Exception) {
            Log.w(TAG, "✗ Failed to load VIN/WMI mirror", e)
            data = data.copy(isLoadingVinWmi = false)
            emit(data)
        }

        Log.d(TAG, "Comprehensive lookup complete - ${data.getLoadedSectionsCount()} sections loaded")
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

        return if (response.isSuccessful && response.body()?.success == true) {
            val records = response.body()?.result?.records
            if (!records.isNullOrEmpty()) {
                parseKmAndFlags(records[0])
            } else {
                null
            }
        } else {
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
        val degemCdFormatted = String.format("%04d", degemCd)

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

    companion object {
        private const val TAG = "VehicleRepository"
    }
}
