package com.example.plateocr.ml.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import com.example.plateocr.data.model.OcrResult

/**
 * ML Kit-based OCR engine for license plate text recognition.
 *
 * Uses Google's ML Kit Text Recognition API to extract text from cropped plate images.
 * Optimized for Israeli license plate format.
 *
 * Israeli Plate Formats:
 * - Standard: XX-XXX-XX (e.g., 12-345-67)
 * - Old: XXX-XX-XXX
 * - Some variations with letters
 *
 * Usage:
 * ```kotlin
 * val ocrEngine = OcrEngine()
 * val result = ocrEngine.recognizeText(croppedPlateBitmap)
 * ocrEngine.close()
 * ```
 */
class OcrEngine : AutoCloseable {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Recognizes text from a license plate image.
     *
     * @param bitmap Cropped license plate image (from YOLO detection)
     * @return OcrResult with extracted text and confidence, or null if recognition fails
     */
    suspend fun recognizeText(bitmap: Bitmap): OcrResult? {
        return try {
            // Convert bitmap to ML Kit InputImage directly (no preprocessing)
            // Note: Tried normalizing to 200px but it reduced accuracy
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            // Run text recognition
            val visionText = recognizer.process(inputImage).await()

            // Extract all text blocks
            val allText = visionText.text
            val blocks = visionText.textBlocks

            android.util.Log.d("OcrEngine", "Recognized text: '$allText'")
            android.util.Log.d("OcrEngine", "Number of blocks: ${blocks.size}")

            if (allText.isBlank()) {
                android.util.Log.w("OcrEngine", "No text detected")
                return null
            }

            // Get confidence (average of all element confidences)
            val confidence = blocks
                .flatMap { it.lines }
                .flatMap { it.elements }
                .mapNotNull { it.confidence }
                .average()
                .toFloat()
                .takeIf { !it.isNaN() } ?: 0f

            // Clean up the text (remove spaces, special chars for plate matching)
            val cleanedText = cleanPlateText(allText)

            // Format for display (xxx-xx-xxx or xx-xxx-xx)
            val formattedText = formatIsraeliPlate(cleanedText)

            android.util.Log.d("OcrEngine", "Cleaned text: '$cleanedText', Formatted: '$formattedText', Confidence: $confidence")

            OcrResult(
                rawText = allText,
                cleanedText = formattedText,
                confidence = confidence,
                isValid = cleanedText.length in 7..8
            )

        } catch (e: Exception) {
            android.util.Log.e("OcrEngine", "OCR failed", e)
            null
        }
    }

    /**
     * Cleans OCR text for Israeli license plate matching.
     *
     * Strategy:
     * 1. Drop everything after the first newline (phone numbers on plate frame)
     * 2. Remove special chars (dashes, colons, dots, etc.)
     * 3. Strip ALL letters entirely (don't convert to digits)
     * 4. Keep only digits
     * 5. Result must be exactly 7 or 8 digits
     *
     * Israeli plates are ALWAYS 7 or 8 digits (no letters in cleaning).
     * Examples:
     * - "33:226-37\nIL\n050-1234567" → "3322637" (7 digits)
     * - "59-039-802" → "59039802" (8 digits)
     */
    private fun cleanPlateText(text: String): String {
        // Step 1: Drop everything after first newline (phone numbers, "IL", etc.)
        var cleaned = text.split("\n").firstOrNull() ?: ""

        // Step 2: Remove spaces and special chars that OCR might add
        cleaned = cleaned
            .replace(" ", "")
            .replace("-", "")
            .replace(".", "")
            .replace(",", "")
            .replace(":", "")  // OCR sometimes sees : instead of digits
            .replace(";", "")
            .replace("_", "")
            .trim()

        // Step 3: Strip ALL letters entirely (don't convert to digits)
        // This handles "IL", "ISR", "ISRAEL" anywhere in the text
        cleaned = cleaned.filter { it.isDigit() }

        android.util.Log.d("OcrEngine", "Text cleaning: '$text' → '$cleaned'")

        return cleaned
    }

    /**
     * Formats Israeli license plate for display.
     *
     * Formats:
     * - 7 digits: xx-xxx-xx (2-3-2, e.g., 12-345-67)
     * - 8 digits: xxx-xx-xxx (3-2-3, e.g., 123-45-678)
     *
     * @param cleanedText Digits-only plate number
     * @return Formatted plate (e.g., "12-345-67" or "123-45-678")
     */
    private fun formatIsraeliPlate(cleanedText: String): String {
        return when (cleanedText.length) {
            7 -> "${cleanedText.substring(0, 2)}-${cleanedText.substring(2, 5)}-${cleanedText.substring(5, 7)}"
            8 -> "${cleanedText.substring(0, 3)}-${cleanedText.substring(3, 5)}-${cleanedText.substring(5, 8)}"
            else -> cleanedText // Return as-is if not standard format
        }
    }

    override fun close() {
        recognizer.close()
    }
}
