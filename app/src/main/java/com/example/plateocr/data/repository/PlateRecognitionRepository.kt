package com.example.plateocr.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.example.plateocr.data.model.DetectionResult
import com.example.plateocr.data.model.OcrResult
import com.example.plateocr.data.model.PlateRecognitionResult
import com.example.plateocr.data.model.VehicleInfo
import com.example.plateocr.ml.detector.PlateDetector
import com.example.plateocr.ml.ocr.OcrEngine
import com.example.plateocr.util.PlateValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository that coordinates license plate recognition operations.
 *
 * This is the single source of truth for plate recognition data, coordinating:
 * - YOLO detection (ml/detector/PlateDetector)
 * - OCR extraction (ml/ocr/OcrEngine)
 * - Text validation (util/PlateValidator)
 * - Backend API calls (data/api/VehicleApiService)
 *
 * Architecture: This follows the Repository pattern in MVVM.
 * ViewModels call this repository, which coordinates between data sources.
 *
 * Usage:
 * ```kotlin
 * val repository = PlateRecognitionRepository(context)
 * val result = repository.recognizePlate(bitmap)
 * ```
 */
class PlateRecognitionRepository(context: Context) {

    private val plateDetector = PlateDetector(context)
    private val ocrEngine = OcrEngine()

    /**
     * Initializes ML models. Call this once during app startup.
     * This loads the YOLO model.
     */
    fun initialize() {
        plateDetector.initialize()
        // ML Kit doesn't need initialization
    }

    /**
     * Processes an image through the complete pipeline:
     * 1. Detect license plate (YOLO)
     * 2. Extract text (OCR)
     * 3. Validate format
     * 4. Lookup vehicle info (API)
     *
     * @param bitmap The image to process
     * @param lookupVehicle Whether to call backend API (default: true)
     * @return Complete recognition result
     */
    suspend fun recognizePlate(
        bitmap: Bitmap,
        lookupVehicle: Boolean = true
    ): Result<PlateRecognitionResult> = withContext(Dispatchers.IO) {
        return@withContext try {
            val startTime = System.currentTimeMillis()

            // Step 1: Detect plate region with YOLO
            val detectionResult = plateDetector.detect(bitmap)

            if (detectionResult == null || !detectionResult.isValid()) {
                return@withContext Result.success(
                    PlateRecognitionResult(
                        detectionResult = detectionResult,
                        ocrResult = null,
                        vehicleInfo = null,
                        processingTimeMs = System.currentTimeMillis() - startTime
                    )
                )
            }

            // Step 2: Crop detected region
            val croppedBitmap = cropBitmap(bitmap, detectionResult.boundingBox)

            // Step 3: Run OCR on cropped region
            // OcrEngine already cleans, formats, and validates the text
            val ocrResult = ocrEngine.recognizeText(croppedBitmap) ?: OcrResult(
                rawText = "",
                cleanedText = "",
                confidence = 0f,
                isValid = false
            )

            // Step 4: If valid and requested, lookup vehicle info
            val vehicleInfo = if (ocrResult.isValid && lookupVehicle) {
                lookupVehicleInfo(ocrResult.cleanedText)
            } else {
                null
            }

            val processingTime = System.currentTimeMillis() - startTime

            Result.success(
                PlateRecognitionResult(
                    detectionResult = detectionResult,
                    ocrResult = ocrResult,
                    vehicleInfo = vehicleInfo,
                    processingTimeMs = processingTime
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Looks up vehicle information from the backend API.
     *
     * @param plateNumber Validated plate number (formatted)
     * @return VehicleInfo if found, null otherwise
     */
    private suspend fun lookupVehicleInfo(plateNumber: String): VehicleInfo? {
        return try {
            // Remove formatting for API call (send just digits)
            val cleanPlate = PlateValidator.cleanPlateText(plateNumber)

            // Use VehicleRepository for consistent API access
            val repository = VehicleRepository()
            val result = repository.lookupByPlate(cleanPlate)

            result.getOrNull()
        } catch (e: Exception) {
            // Network error or backend unavailable, return null
            null
        }
    }

    /**
     * Crops a bitmap to the detected region with some padding.
     *
     * @param source Original image
     * @param region Detected bounding box
     * @param padding Padding around the box (default: 10% of box size)
     * @return Cropped bitmap
     */
    private fun cropBitmap(
        source: Bitmap,
        region: android.graphics.RectF,
        padding: Float = 0.1f
    ): Bitmap {
        // Add padding
        val paddingX = region.width() * padding
        val paddingY = region.height() * padding

        val left = (region.left - paddingX).toInt().coerceAtLeast(0)
        val top = (region.top - paddingY).toInt().coerceAtLeast(0)
        val right = (region.right + paddingX).toInt().coerceAtMost(source.width)
        val bottom = (region.bottom + paddingY).toInt().coerceAtMost(source.height)

        val width = (right - left).coerceAtMost(source.width - left)
        val height = (bottom - top).coerceAtMost(source.height - top)

        return Bitmap.createBitmap(source, left, top, width, height)
    }

    /**
     * Releases ML model resources.
     * Call this when shutting down the app or when models are no longer needed.
     */
    fun close() {
        plateDetector.close()
        ocrEngine.close()
    }
}
