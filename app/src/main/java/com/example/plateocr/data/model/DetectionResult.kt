package com.example.plateocr.data.model

import android.graphics.RectF

/**
 * Represents a license plate detection result from YOLO model.
 *
 * @property boundingBox The detected plate region in the image
 * @property confidence Detection confidence (0.0 to 1.0)
 * @property imageWidth Width of the source image
 * @property imageHeight Height of the source image
 */
data class DetectionResult(
    val boundingBox: RectF,
    val confidence: Float,
    val imageWidth: Int,
    val imageHeight: Int
) {
    /**
     * Returns true if this detection is likely valid based on confidence threshold.
     */
    fun isValid(threshold: Float = 0.5f): Boolean {
        return confidence >= threshold &&
               boundingBox.width() > 0 &&
               boundingBox.height() > 0
    }

    /**
     * Calculates the area of the bounding box as a percentage of the image.
     */
    fun areaPercentage(): Float {
        val boxArea = boundingBox.width() * boundingBox.height()
        val imageArea = imageWidth * imageHeight
        return (boxArea / imageArea) * 100f
    }
}
