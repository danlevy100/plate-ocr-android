package com.example.plateocr.util

import android.content.Context
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Field configuration data classes
 */
@JsonClass(generateAdapter = true)
data class FieldConfigRoot(
    val sections: List<SectionConfig>
)

@JsonClass(generateAdapter = true)
data class SectionConfig(
    val id: Int,
    @Json(name = "name_hebrew")
    val nameHebrew: String,
    @Json(name = "name_english")
    val nameEnglish: String,
    @Json(name = "initially_expanded")
    val initiallyExpanded: Boolean,
    val fields: List<FieldConfig>
)

@JsonClass(generateAdapter = true)
data class FieldConfig(
    @Json(name = "field_name")
    val fieldName: String,
    @Json(name = "display_name_hebrew")
    val displayNameHebrew: String,
    val display: Boolean = true,
    @Json(name = "is_custom")
    val isCustom: Boolean = false,
    val type: String? = null,  // e.g., "mapped_text", "mapped_numeric", "date", "number", "text", "boolean", "boolean_existence", "float_one_decimal"
    @Json(name = "value_mappings")
    val valueMappings: Map<String, String>? = null,
    @Json(name = "existence_display_value")
    val existenceDisplayValue: String? = null,  // For boolean_existence type: value to show when field exists
    @Json(name = "non_existence_display_value")
    val nonExistenceDisplayValue: String? = null,  // For boolean_existence type: value to show when field is null/doesn't exist
    @Transient
    var order: Int = 0  // Set from array index during loading
) {
    /**
     * Infer type from field name if not explicitly set
     */
    fun getInferredType(): String {
        // Explicit type takes precedence
        if (type != null) return type

        // Infer from field name
        return when {
            fieldName.endsWith("_ind") || fieldName.endsWith("_source") ||
            fieldName == "alco_lock" -> "boolean"
            fieldName.endsWith("_dt") -> "date"
            fieldName.endsWith("_cd") || fieldName.endsWith("_nm") -> "text"
            else -> "text"  // Default to text for safety
        }
    }
}

/**
 * Singleton to load and manage field configuration from JSON
 */
object FieldConfigManager {
    private var config: FieldConfigRoot? = null
    private val fieldNameToConfig = mutableMapOf<String, FieldConfig>()
    private val sectionIdToConfig = mutableMapOf<Int, SectionConfig>()

    fun load(context: Context) {
        if (config != null) return // Already loaded

        try {
            val json = context.assets.open("field_config.json").bufferedReader().use { it.readText() }
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
            val adapter = moshi.adapter(FieldConfigRoot::class.java)
            config = adapter.fromJson(json)

            // Build lookup maps and set order from array index
            config?.sections?.forEach { section ->
                sectionIdToConfig[section.id] = section
                section.fields.forEachIndexed { index, field ->
                    field.order = index  // Use array position as order
                    fieldNameToConfig[field.fieldName] = field
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FieldConfigManager", "Failed to load field config", e)
        }
    }

    /**
     * Get Hebrew display name for a field
     */
    fun getDisplayName(fieldName: String): String {
        return fieldNameToConfig[fieldName]?.displayNameHebrew ?: fieldName
    }

    /**
     * Check if field should be displayed
     */
    fun shouldDisplay(fieldName: String): Boolean {
        return fieldNameToConfig[fieldName]?.display ?: true
    }

    /**
     * Get mapped value for a field, or original value if no mapping exists
     * Supports both string and numeric values
     */
    fun getMappedValue(fieldName: String, value: String): String {
        return fieldNameToConfig[fieldName]?.valueMappings?.get(value) ?: value
    }

    /**
     * Get mapped value for numeric field, converting to string for lookup
     */
    fun getMappedValue(fieldName: String, value: Number): String {
        val stringValue = if (value.toDouble() == value.toDouble().toLong().toDouble()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
        return fieldNameToConfig[fieldName]?.valueMappings?.get(stringValue) ?: stringValue
    }

    /**
     * Get ordered field names for a section
     */
    fun getOrderedFieldNames(sectionId: Int): List<String> {
        return sectionIdToConfig[sectionId]
            ?.fields
            ?.sortedBy { it.order }
            ?.map { it.fieldName }
            ?: emptyList()
    }

    /**
     * Get field order for sorting
     */
    fun getFieldOrder(fieldName: String): Int {
        return fieldNameToConfig[fieldName]?.order ?: Int.MAX_VALUE
    }

    /**
     * Check if field is custom (calculated)
     */
    fun isCustomField(fieldName: String): Boolean {
        return fieldNameToConfig[fieldName]?.isCustom ?: false
    }

    /**
     * Get section config by ID
     */
    fun getSectionConfig(sectionId: Int): SectionConfig? {
        return sectionIdToConfig[sectionId]
    }

    /**
     * Get all section configs
     */
    fun getAllSections(): List<SectionConfig> {
        return config?.sections ?: emptyList()
    }

    /**
     * Get existence display value for boolean_existence type fields
     * Returns null if field is not boolean_existence type
     */
    fun getExistenceDisplayValue(fieldName: String): String? {
        val field = fieldNameToConfig[fieldName] ?: return null
        return if (field.type == "boolean_existence") {
            field.existenceDisplayValue
        } else {
            null
        }
    }

    /**
     * Get non-existence display value for boolean_existence type fields
     * Returns null if field is not boolean_existence type
     */
    fun getNonExistenceDisplayValue(fieldName: String): String? {
        val field = fieldNameToConfig[fieldName] ?: return null
        return if (field.type == "boolean_existence") {
            field.nonExistenceDisplayValue
        } else {
            null
        }
    }

    /**
     * Check if field should be formatted as float with one decimal place
     */
    fun isFloatOneDecimal(fieldName: String): Boolean {
        return fieldNameToConfig[fieldName]?.type == "float_one_decimal"
    }
}
