package com.example.plateocr.util

/**
 * Validates and cleans Israeli license plate numbers.
 *
 * Israeli license plates follow specific formats:
 * - 7 digits: XXX-XX-XXX (old format)
 * - 8 digits: XXX-XX-XXXX (new format)
 *
 * This validator handles:
 * - Text cleaning (removing spaces, dashes, non-numeric characters)
 * - Format validation
 * - Confidence scoring based on detection quality
 */
object PlateValidator {

    /**
     * Validates if a string is a valid Israeli license plate.
     *
     * @param text The text to validate
     * @return true if the text is a valid Israeli plate format
     */
    fun isValidPlate(text: String): Boolean {
        val cleaned = cleanPlateText(text)
        return cleaned.length in 7..8 && cleaned.all { it.isDigit() }
    }

    /**
     * Cleans plate text by removing all non-numeric characters.
     *
     * Examples:
     * - "123-45-678" -> "12345678"
     * - "123 45 678" -> "12345678"
     * - "12-34-567" -> "1234567"
     *
     * @param text The raw OCR text
     * @return Cleaned numeric string
     */
    fun cleanPlateText(text: String): String {
        return text.filter { it.isDigit() }
    }

    /**
     * Formats a plate number in the standard display format.
     *
     * @param plateNumber The cleaned plate number (7-8 digits)
     * @return Formatted string (e.g., "12-345-67" or "123-45-678")
     */
    fun formatPlate(plateNumber: String): String {
        val cleaned = cleanPlateText(plateNumber)
        return when (cleaned.length) {
            7 -> "${cleaned.substring(0, 2)}-${cleaned.substring(2, 5)}-${cleaned.substring(5, 7)}"  // 2-3-2
            8 -> "${cleaned.substring(0, 3)}-${cleaned.substring(3, 5)}-${cleaned.substring(5, 8)}"  // 3-2-3
            else -> cleaned
        }
    }

    /**
     * Validates OCR result and calculates confidence score.
     *
     * Confidence factors:
     * - Length (7-8 digits): +40 points
     * - All digits: +30 points
     * - No spaces/special chars: +20 points
     * - Clear OCR text (no ambiguous characters): +10 points
     *
     * @param text The OCR result text
     * @return Pair of (isValid, confidenceScore 0-100)
     */
    fun validateWithConfidence(text: String): Pair<Boolean, Int> {
        val cleaned = cleanPlateText(text)
        var confidence = 0

        // Length check
        if (cleaned.length in 7..8) {
            confidence += 40
        } else {
            return Pair(false, 0)
        }

        // All digits check
        if (cleaned.all { it.isDigit() }) {
            confidence += 30
        } else {
            return Pair(false, confidence)
        }

        // Original text quality (fewer non-digit characters = better OCR)
        val nonDigitCount = text.count { !it.isDigit() }
        if (nonDigitCount <= 2) {
            confidence += 20
        } else if (nonDigitCount <= 4) {
            confidence += 10
        }

        // Ambiguous character check (common OCR mistakes)
        val hasAmbiguous = text.any { it in listOf('O', 'o', 'I', 'l', 'S', 'B') }
        if (!hasAmbiguous) {
            confidence += 10
        }

        return Pair(true, confidence.coerceIn(0, 100))
    }

    /**
     * Extracts the best plate number from multiple OCR results.
     *
     * @param candidates List of OCR result candidates
     * @return The best valid plate number, or null if none are valid
     */
    fun selectBestCandidate(candidates: List<String>): String? {
        return candidates
            .map { text ->
                val (isValid, confidence) = validateWithConfidence(text)
                Triple(text, isValid, confidence)
            }
            .filter { it.second } // Only valid plates
            .maxByOrNull { it.third } // Highest confidence
            ?.let { cleanPlateText(it.first) }
    }
}
