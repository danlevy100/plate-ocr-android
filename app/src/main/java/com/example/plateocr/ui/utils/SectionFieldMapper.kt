package com.example.plateocr.ui.utils

import com.example.plateocr.data.model.gov.AggregateVehicleData

/**
 * Maps raw field data to organized sections matching GovCar structure
 */
object SectionFieldMapper {

    // Section 1: Basic Information fields
    val basicInfoFields = setOf(
        "tozeret_nm", "tozeret_cd", "kinuy_mishari", "degem_nm", "degem_cd", "degem_manoa",
        "sug_degem", "shnat_yitzur", "moed_aliya_lakvish", "misgeret",
        "tzeva_rechev", "tzeva_cd", "ramat_gimur", "merkav", "tozar",
        "tozeret_eretz_nm", "rishum_rishon_dt"
    )

    // Section 2: Important Details fields (from vehicle)
    val importantDetailsVehicleFields = setOf(
        "baalut", "tokef_dt", "mivchan_acharon_dt"
    )

    // Section 2: Important Details fields (from kmAndFlags)
    val importantDetailsKmFields = setOf(
        "kilometer_test_aharon", "mkoriut_nm"
    )

    // Section 2: Important Details fields (from disabledTag)
    val importantDetailsDisabledTagFields = setOf(
        "SUG_TOV"  // Disabled parking permit type
    )

    // Section 3: Engine & Mechanical fields
    val engineMechanicalFields = setOf(
        "nefah_manoa", "delek_nm", "sug_delek_nm", "koah_sus",
        "hanaa_nm", "technologiat_hanaa_nm", "mispar_manoa",
        "automatic_ind", "sug_mamir_nm"
    )

    // Section 4: Dimensions & Weights fields
    val dimensionsWeightsFields = setOf(
        "mishkal_kolel", "kosher_grira_im_blamim", "kosher_grira_bli_blamim",
        "zmig_kidmi", "zmig_ahori", "gova"
    )

    // Section 5: Equipment - counts (key-value)
    val equipmentCountFields = setOf(
        "mispar_moshavim", "mispar_dlatot", "mispar_halonot_hashmal",
        "mispar_kariot_avir", "sug_tkina_nm", "horaat_rishum"
    )

    // Section 5: Equipment - features (boolean)
    val equipmentFeatureFields = setOf(
        "halon_bagg_ind", "mazgan_ind", "hege_koah_ind",
        "galgaley_sagsoget_kala_ind", "argaz_ind", "matzlemat_reverse_ind"
    )

    // Section 6: Safety fields (boolean)
    val safetyFields = setOf(
        "abs_ind", "bakarat_yatzivut_ind", "maarechet_ezer_labalam_ind",
        "bakarat_shyut_adaptivit_ind", "bakarat_stiya_menativ_ind",
        "bakarat_stiya_activ_s", "hayshaney_hagorot_ind",
        "hayshaney_lahatz_avir_batzmigim_ind", "zihuy_holchey_regel_ind",
        "zihuy_rechev_do_galgali", "blimat_hirum_lifnei_holhei_regel_ofanaim",
        "blima_otomatit_nesia_leahor", "zihuy_tamrurey_tnua_ind",
        "bakarat_mehirut_isa", "shlita_automatit_beorot_gvohim_ind",
        "nitur_merhak_milfanim_ind", "hitnagshut_cad_shetah_met",
        "zihuy_beshetah_nistar_ind", "zihuy_matzav_hitkarvut_mesukenet_ind",
        "nikud_betihut", "ramat_eivzur_betihuty"
    )

    // Section 7: Environment fields
    val environmentFields = setOf(
        "kvutzat_zihum", "madad_yarok", "CO2_WLTP", "NOX_WLTP",
        "CO_WLTP", "HC_WLTP", "PM_WLTP", "tzrihat_delek_ironit",
        "tzrihat_delek_kvish_huz", "tzrihat_delek_meshuleshet",
        "tevah_hashmalit_wltp"
    )

    /**
     * Filter fields for a specific section
     */
    fun filterFields(allFields: Map<String, Any?>, allowedFields: Set<String>): Map<String, Any?> {
        return allFields.filterKeys { it in allowedFields }
    }

    /**
     * Calculate "יד" (hand) from ownership history
     * Counts all ownership entries, excluding only "סוחר"
     * Returns count as a plain integer string
     */
    fun calculateYad(data: AggregateVehicleData): String? {
        val history = data.ownershipHistory ?: return null
        if (history.isEmpty()) return null

        // Count all ownership entries, excluding only "סוחר"
        val count = history.count {
            it.ownershipType != null && !it.ownershipType.contains("סוחר", ignoreCase = true)
        }

        // Return count as plain integer
        return count.toString()
    }
}
