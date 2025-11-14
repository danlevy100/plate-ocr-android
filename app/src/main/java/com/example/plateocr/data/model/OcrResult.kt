package com.example.plateocr.data.model

/**
 * Represents an OCR result for extracted license plate text.
 *
 * @property rawText The raw text extracted by the OCR engine
 * @property cleanedText The cleaned and validated text
 * @property confidence OCR confidence score (0.0 to 1.0)
 * @property isValid Whether the text passes Israeli plate validation
 */
data class OcrResult(
    val rawText: String,
    val cleanedText: String,
    val confidence: Float,
    val isValid: Boolean
) {
    /**
     * Returns formatted plate number if valid, otherwise raw text.
     */
    fun getDisplayText(): String {
        return if (isValid) cleanedText else rawText
    }
}
