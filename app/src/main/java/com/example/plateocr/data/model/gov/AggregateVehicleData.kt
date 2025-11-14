package com.example.plateocr.data.model.gov

import com.example.plateocr.data.model.VehicleInfo

/**
 * Comprehensive vehicle data aggregated from multiple government sources.
 * Data is loaded progressively as API calls complete.
 */
data class AggregateVehicleData(
    // Basic vehicle info (always loaded first)
    val vehicle: VehicleInfo? = null,

    // Progressive data sources
    val ownershipHistory: List<OwnershipHistory>? = null,
    val wltpSpecs: WltpSpecs? = null,
    val recalls: List<VehicleRecall>? = null,
    val kmAndFlags: KmAndFlags? = null,
    val importerPrice: ImporterPrice? = null,
    val modelMonthlyCounts: List<ModelMonthlyCounts>? = null,
    val disabledTag: DisabledTag? = null,
    val euTypeApprovalA: List<EuTypeApproval>? = null,
    val euTypeApprovalB: List<EuTypeApproval>? = null,
    val plateUpdates: List<PlateUpdate>? = null,
    val vinWmiMirror: VinWmiMirror? = null,

    // Loading states for each section
    val isLoadingOwnershipHistory: Boolean = false,
    val isLoadingWltpSpecs: Boolean = false,
    val isLoadingRecalls: Boolean = false,
    val isLoadingKmAndFlags: Boolean = false,
    val isLoadingImporterPrice: Boolean = false,
    val isLoadingModelCounts: Boolean = false,
    val isLoadingDisabledTag: Boolean = false,
    val isLoadingEuType: Boolean = false,
    val isLoadingPlateUpdates: Boolean = false,
    val isLoadingVinWmi: Boolean = false
) {
    /**
     * Get MSRP from either importer price or vehicle's horaat_rishum field
     */
    fun getMsrp(): Int? {
        return importerPrice?.requestedPrice?.toInt() ?: vehicle?.registrationInstruction
    }

    /**
     * Calculate monthly use value (2.48% of MSRP per month)
     */
    fun getUseValueMonthly(): Int? {
        return getMsrp()?.let { (it * 0.0248).toInt() }
    }
    /**
     * Get total number of vehicles of this model on the road
     */
    fun getTotalVehiclesOnRoad(): Int {
        return modelMonthlyCounts?.sumOf { it.carNum ?: 0 } ?: 0
    }

    /**
     * Returns true if all data sources have finished loading (success or failure)
     */
    fun isFullyLoaded(): Boolean {
        return !isLoadingOwnershipHistory &&
               !isLoadingWltpSpecs &&
               !isLoadingRecalls &&
               !isLoadingKmAndFlags &&
               !isLoadingImporterPrice &&
               !isLoadingModelCounts &&
               !isLoadingDisabledTag &&
               !isLoadingEuType &&
               !isLoadingPlateUpdates &&
               !isLoadingVinWmi
    }

    /**
     * Returns the number of data sources that have been loaded
     */
    fun getLoadedSectionsCount(): Int {
        var count = if (vehicle != null) 1 else 0
        if (ownershipHistory != null) count++
        if (wltpSpecs != null) count++
        if (recalls != null) count++
        if (kmAndFlags != null) count++
        if (importerPrice != null) count++
        if (modelMonthlyCounts != null) count++
        if (disabledTag != null) count++
        if (euTypeApprovalA != null || euTypeApprovalB != null) count++
        if (plateUpdates != null) count++
        if (vinWmiMirror != null) count++
        return count
    }
}
