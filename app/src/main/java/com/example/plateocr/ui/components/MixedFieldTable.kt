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

/**
 * Displays a mix of API fields and custom calculated fields
 * API fields are translated via CSV, custom fields use their display names directly
 */
@Composable
fun MixedFieldTable(
    apiFields: Map<String, Any?>,
    modifier: Modifier = Modifier,
    customFields: Map<String, String> = emptyMap()
) {
    val context = LocalContext.current
    FieldTranslations.load(context)
    FieldConfigManager.load(context)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(modifier = modifier) {
            val allEntries = mutableListOf<Triple<String, String, Int>>()  // field, value, order

            // Add API fields (filtered and translated)
            apiFields
                .filter { (key, _) -> FieldConfigManager.shouldDisplay(key) }
                .forEach { (key, value) ->
                    // Skip boolean fields that are false (0)
                    if (isBooleanField(key) && value is Number && value.toDouble() != 1.0) {
                        return@forEach
                    }

                    val formattedValue = formatApiValue(key, value)
                    if (formattedValue != "—") {  // Only add if not empty
                        allEntries.add(
                            Triple(
                                FieldConfigManager.getDisplayName(key),
                                formattedValue,
                                FieldConfigManager.getFieldOrder(key)
                            )
                        )
                    }
                }

            // Add custom fields with their order from config
            customFields.forEach { (name, value) ->
                // Determine the field key for config lookup
                val fieldKey = when {
                    // Levi Itzhak fields already have __levi_ prefix
                    name.startsWith("__levi_") -> name
                    // Other custom fields need __custom_ prefix
                    else -> "__custom_" + when(name) {
                        "יד" -> "yad"
                        "מחיר יבואן (MSRP)" -> "msrp"
                        "שווי שימוש חודשי" -> "use_value"
                        "תו נכה" -> "disabled_tag"
                        "ריקולים" -> "recalls"
                        "סה״כ רכבים מדגם זה בכביש" -> "total_vehicles"
                        else -> name
                    }
                }

                // Check if field should be displayed
                if (!FieldConfigManager.shouldDisplay(fieldKey)) {
                    return@forEach
                }

                // Use display name from config if available, otherwise use the original name
                val displayName = FieldConfigManager.getDisplayName(fieldKey)
                allEntries.add(Triple(displayName, value, FieldConfigManager.getFieldOrder(fieldKey)))
            }

            // Sort by order and display
            val sortedEntries = allEntries.sortedBy { it.third }

            // Display all rows
            sortedEntries.forEachIndexed { index, (name, value, _) ->
                SimpleFieldRow(fieldName = name, fieldValue = value)
                if (index < sortedEntries.size - 1) {
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
private fun SimpleFieldRow(
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
 * Format API values using same logic as FieldTable
 */
private fun formatApiValue(fieldName: String, value: Any?): String {
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
                val plateStr = value.toLong().toString()
                return when (plateStr.length) {
                    7 -> "${plateStr.substring(0, 2)}-${plateStr.substring(2, 5)}-${plateStr.substring(5, 7)}"
                    8 -> "${plateStr.substring(0, 3)}-${plateStr.substring(3, 5)}-${plateStr.substring(5, 8)}"
                    else -> plateStr
                }
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
                return String.format(java.util.Locale.US, "%.1f", value.toDouble())
            }

            // Mileage: add comma separators for numbers > 999
            if (fieldName == "kilometer_test_aharon" && value.toLong() > 999) {
                return String.format(java.util.Locale.US, "%,d", value.toLong())
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
                        try {
                            val year = value.substring(0, 4)
                            val month = value.substring(4, 6)
                            val day = value.substring(6, 8)
                            "$year/$month/$day"
                        } catch (_: Exception) {
                            value
                        }
                    }
                    // YYYYMM format (e.g., moed_aliya_lakvish as 6 digits)
                    fieldName == "moed_aliya_lakvish" && value.length == 6 && value.all { it.isDigit() } -> {
                        try {
                            val year = value.substring(0, 4)
                            val month = value.substring(4, 6)
                            "$year-$month"
                        } catch (_: Exception) {
                            value
                        }
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
           fieldName == "alco_lock"
}
