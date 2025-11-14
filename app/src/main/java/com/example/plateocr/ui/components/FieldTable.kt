package com.example.plateocr.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.plateocr.util.FieldTranslations
import com.example.plateocr.util.FieldConfigManager
import java.text.NumberFormat
import java.util.Locale

/**
 * Displays fields from API response as a key-value table
 * Uses CSV translations to show Hebrew names and filter by display flag
 */
@Composable
fun FieldTable(
    fields: Map<String, Any?>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Load configurations
    FieldTranslations.load(context)
    FieldConfigManager.load(context)

    // Force RTL layout for Hebrew content
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(modifier = modifier) {
            // Filter fields by display flag, exclude false boolean fields, and sort by JSON order
            val filteredFields = fields
                .filter { (key, value) ->
                    // Must be in display list (from JSON config)
                    if (!FieldConfigManager.shouldDisplay(key)) return@filter false

                    // Skip boolean fields that are false (0)
                    if (isBooleanField(key) && value is Number && value.toDouble() != 1.0) {
                        return@filter false
                    }

                    true
                }
                .entries
                .sortedBy { FieldConfigManager.getFieldOrder(it.key) }

            filteredFields.forEachIndexed { index, (key, value) ->
                FieldRow(
                    fieldName = FieldConfigManager.getDisplayName(key),
                    fieldValue = formatValue(key, value)
                )
                if (index < filteredFields.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldRow(
    fieldName: String,
    fieldValue: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = fieldName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = fieldValue,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start
        )
    }
}

/**
 * Format field values for display (matching web app formatting)
 * Special handling for license plates, mileage fields, booleans, WLTP fields, and dates
 */
private fun formatValue(fieldName: String, value: Any?): String {
    return when (value) {
        null -> {
            // Boolean existence fields: show non-existence value when null
            FieldConfigManager.getNonExistenceDisplayValue(fieldName)?.let { nonExistenceValue ->
                return nonExistenceValue
            }
            "—"
        }
        is Number -> {
            // License plate: format as xx-xxx-xx (7 digits) or xxx-xx-xxx (8 digits)
            if (fieldName == "mispar_rechev") {
                return formatLicensePlate(value.toLong())
            }

            // Check for value mappings in config for numeric values (e.g., automatic_ind: 1 -> אוטומטי)
            val mappedValue = FieldConfigManager.getMappedValue(fieldName, value)
            if (mappedValue != value.toString() && mappedValue != value.toLong().toString()) {
                return mappedValue
            }

            // Boolean fields: return empty string - we only show the field name
            if (isBooleanField(fieldName)) {
                return ""
            }

            // Float with one decimal place (e.g., safety score)
            if (FieldConfigManager.isFloatOneDecimal(fieldName)) {
                return String.format("%.1f", value.toDouble())
            }

            // WLTP and emission fields: show as integers with commas
            if (isWltpOrEmissionField(fieldName) && value.toLong() > 999) {
                return NumberFormat.getNumberInstance(Locale.US).format(value.toLong())
            }

            // Mileage: add comma separators for numbers > 999
            if (fieldName == "kilometer_test_aharon" && value.toLong() > 999) {
                return NumberFormat.getNumberInstance(Locale.US).format(value)
            }

            // All other numbers: display as integers without .0 suffix
            value.toLong().toString()
        }
        is String -> {
            if (value.isBlank()) {
                "—"
            } else {
                // Boolean existence fields: show configured existence value if field exists
                FieldConfigManager.getExistenceDisplayValue(fieldName)?.let { existenceValue ->
                    return existenceValue
                }

                // Filter out "לא ידוע קוד 0" for sug_mamir_nm
                if (fieldName == "sug_mamir_nm" && value == "לא ידוע קוד 0") {
                    return "—"
                }

                // Check for value mappings in config (e.g., sug_degem: P -> פרטי מתחת 3.5 טון)
                val mappedValue = FieldConfigManager.getMappedValue(fieldName, value)
                if (mappedValue != value) {
                    return mappedValue
                }

                // Format date strings based on field name
                when {
                    // YYYYMMDD format (e.g., tokef_dt, mivchan_acharon_dt, rishum_rishon_dt)
                    fieldName.endsWith("_dt") && value.length == 8 && value.all { it.isDigit() } -> {
                        formatDateYYYYMMDD(value)
                    }
                    // YYYYMM format (e.g., moed_aliya_lakvish as 6 digits)
                    fieldName == "moed_aliya_lakvish" && value.length == 6 && value.all { it.isDigit() } -> {
                        formatDateYYYYMM(value)
                    }
                    else -> value
                }
            }
        }
        else -> value.toString()
    }
}

/**
 * Check if field is a boolean (indicator/flag) field
 */
private fun isBooleanField(fieldName: String): Boolean {
    return fieldName.endsWith("_ind") ||
           fieldName.endsWith("_source") ||
           fieldName == "alco_lock" ||
           fieldName == "bakarat_mehirut_isa" ||
           fieldName == "bakarat_stiya_activ_s" ||
           fieldName == "blima_otomatit_nesia_leahor" ||
           fieldName == "blimat_hirum_lifnei_holhei_regel_ofanaim" ||
           fieldName == "hitnagshut_cad_shetah_met" ||
           fieldName == "zihuy_rechev_do_galgali" ||
           fieldName == "teura_automatit_benesiya_kadima_ind"
}

/**
 * Check if field is a WLTP emission or consumption field
 */
private fun isWltpOrEmissionField(fieldName: String): Boolean {
    return fieldName.startsWith("CO2_") ||
           fieldName.startsWith("CO_") ||
           fieldName.startsWith("HC_") ||
           fieldName.startsWith("NOX_") ||
           fieldName.startsWith("PM_") ||
           fieldName.startsWith("kamut_") ||
           fieldName.startsWith("tzrihat_delek_") ||
           fieldName.startsWith("tevah_hashmalit_") ||
           fieldName == "koah_sus" ||
           fieldName == "nefah_manoa" ||
           fieldName == "mishkal_kolel" ||
           fieldName == "kosher_grira_bli_blamim" ||
           fieldName == "kosher_grira_im_blamim" ||
           fieldName == "mehir" ||
           fieldName == "mehir_mevukash"
}

/**
 * Format license plate number as xx-xxx-xx (7 digits) or xxx-xx-xxx (8 digits)
 */
private fun formatLicensePlate(plateNumber: Long): String {
    val plateStr = plateNumber.toString()
    return when (plateStr.length) {
        7 -> "${plateStr.substring(0, 2)}-${plateStr.substring(2, 5)}-${plateStr.substring(5, 7)}"
        8 -> "${plateStr.substring(0, 3)}-${plateStr.substring(3, 5)}-${plateStr.substring(5, 8)}"
        else -> plateStr
    }
}

/**
 * Format YYYYMMDD date string to YYYY/MM/DD
 */
private fun formatDateYYYYMMDD(date: String): String {
    return try {
        val year = date.substring(0, 4)
        val month = date.substring(4, 6)
        val day = date.substring(6, 8)
        "$year/$month/$day"
    } catch (e: Exception) {
        date // Return original if parsing fails
    }
}

/**
 * Format YYYYMM date string to YYYY-MM
 */
private fun formatDateYYYYMM(date: String): String {
    return try {
        val year = date.substring(0, 4)
        val month = date.substring(4, 6)
        "$year-$month"
    } catch (e: Exception) {
        date // Return original if parsing fails
    }
}
