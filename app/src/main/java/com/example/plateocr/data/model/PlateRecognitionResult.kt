package com.example.plateocr.data.model

/**
 * Complete result of the license plate recognition pipeline.
 *
 * This combines detection, OCR, and vehicle lookup results.
 *
 * @property detectionResult YOLO detection result (bounding box, confidence)
 * @property ocrResult OCR extraction result (text, validation)
 * @property vehicleInfo Optional vehicle information from backend
 * @property processingTimeMs Total processing time in milliseconds
 */
data class PlateRecognitionResult(
    val detectionResult: DetectionResult?,
    val ocrResult: OcrResult?,
    val vehicleInfo: VehicleInfo? = null,
    val processingTimeMs: Long = 0
) {
    /**
     * Overall confidence combining detection and OCR scores.
     */
    fun overallConfidence(): Float {
        val detectionConf = detectionResult?.confidence ?: 0f
        val ocrConf = ocrResult?.confidence ?: 0f
        return (detectionConf + ocrConf) / 2f
    }

    /**
     * Returns true if we have a valid plate number.
     */
    fun isSuccess(): Boolean {
        return ocrResult?.isValid == true
    }

    /**
     * Returns the recognized plate number if available.
     */
    fun getPlateNumber(): String? {
        return ocrResult?.cleanedText
    }

    /**
     * Returns a human-readable status message.
     */
    fun getStatusMessage(): String {
        return when {
            detectionResult == null -> "No license plate detected"
            ocrResult == null -> "Could not read plate text"
            !ocrResult.isValid -> "Invalid plate format: ${ocrResult.rawText}"
            vehicleInfo?.isValid() == false -> "Vehicle not found"
            vehicleInfo != null -> "Vehicle found: ${vehicleInfo.getDisplayName()}"
            else -> "Plate: ${ocrResult.cleanedText}"
        }
    }
}
