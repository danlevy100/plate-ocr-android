package com.example.plateocr.util

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Field translation entry from CSV
 */
data class FieldTranslation(
    val fieldName: String,
    val hebrewTranslation: String,
    val display: Boolean
)

/**
 * Loads and manages field translations from CSV file in assets
 */
object FieldTranslations {
    private var translations: Map<String, FieldTranslation>? = null

    /**
     * Load translations from CSV file in assets folder
     */
    fun load(context: Context) {
        if (translations != null) return

        val translationMap = mutableMapOf<String, FieldTranslation>()

        try {
            context.assets.open("field_translations.csv").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    // Skip header line
                    reader.readLine()

                    // Read each line
                    reader.forEachLine { line ->
                        if (line.isBlank()) return@forEachLine

                        val parts = line.split(",")
                        if (parts.size >= 3) {
                            val fieldName = parts[0].trim()
                            val hebrewTranslation = parts[1].trim()
                            val display = parts[2].trim() == "1"

                            if (fieldName.isNotEmpty()) {
                                translationMap[fieldName] = FieldTranslation(
                                    fieldName = fieldName,
                                    hebrewTranslation = hebrewTranslation,
                                    display = display
                                )
                            }
                        }
                    }
                }
            }

            translations = translationMap
        } catch (e: Exception) {
            // If CSV not found or parsing fails, use empty map
            translations = emptyMap()
        }
    }

    /**
     * Get Hebrew translation for a field name
     * Returns the original field name if no translation found
     */
    fun getHebrewName(fieldName: String): String {
        return translations?.get(fieldName)?.hebrewTranslation ?: fieldName
    }

    /**
     * Check if a field should be displayed
     * Returns true by default if field not found in CSV
     */
    fun shouldDisplay(fieldName: String): Boolean {
        return translations?.get(fieldName)?.display ?: true
    }

    /**
     * Filter and translate a map of fields
     * Returns map with Hebrew names for keys, filtered by display flag
     */
    fun filterAndTranslate(fields: Map<String, Any?>): Map<String, Any?> {
        return fields
            .filter { (key, _) -> shouldDisplay(key) }
            .mapKeys { (key, _) -> getHebrewName(key) }
    }
}
