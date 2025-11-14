package com.example.plateocr.ml.detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.example.plateocr.data.model.DetectionResult
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * YOLO-based license plate detector using ONNX Runtime.
 *
 * Model Details:
 * - Source: /home/dan/yad2scraper/runs/israeli_plates/train3/weights/best.pt
 * - Format: ONNX (converted from PyTorch)
 * - Input: [1, 3, 640, 640] - CHW format (channels-first), RGB, normalized [0,1]
 * - Output: [1, 5, 8400] - [batch, (x, y, w, h, conf), anchors]
 *
 * This is the production model actually used in the yad2scraper project.
 * Trained specifically on Israeli license plates.
 *
 * Usage:
 * ```kotlin
 * val detector = PlateDetector(context)
 * val result = detector.detect(bitmap)
 * detector.close()
 * ```
 */
class PlateDetector(private val context: Context) : AutoCloseable {

    private val ortEnvironment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null
    private var isInitialized = false

    companion object {
        // Model configuration
        const val MODEL_FILE = "yolo_plate_detector.onnx"
        const val INPUT_SIZE = 640
        const val CONFIDENCE_THRESHOLD = 0.1f  // Lowered for debugging - was 0.5f
        const val IOU_THRESHOLD = 0.45f  // For NMS (Non-Maximum Suppression)

        // YOLO output format: [1, 5, 8400]
        // 5 = [x_center, y_center, width, height, confidence]
        // 8400 = number of anchor points
        const val NUM_OUTPUTS = 5
        const val NUM_ANCHORS = 8400
    }

    init {
        initialize()
    }

    /**
     * Initializes the ONNX Runtime model.
     * Loads the model from assets on first use.
     *
     * Note: Called automatically in init block, but can be called explicitly if needed.
     *
     * @throws IllegalStateException if model file is not found
     */
    fun initialize() {
        if (isInitialized) return

        try {
            // Load model from assets
            val modelBytes = context.assets.open(MODEL_FILE).use { it.readBytes() }

            // Create ONNX session
            val sessionOptions = OrtSession.SessionOptions().apply {
                // Use all available CPU threads for better performance
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors())

                // Optional: Enable performance optimizations
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }

            ortSession = ortEnvironment.createSession(modelBytes, sessionOptions)
            isInitialized = true

        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to load YOLO model. Make sure $MODEL_FILE exists in assets/",
                e
            )
        }
    }

    /**
     * Detects license plates in the given image.
     *
     * Process:
     * 1. Add padding if image is too "zoomed in" (plate fills >60% of frame)
     * 2. Preprocess: Resize to 640x640, convert to CHW format, normalize
     * 3. Run ONNX inference
     * 4. Parse output tensor [1, 5, 8400]
     * 5. Apply NMS to remove overlapping detections
     * 6. Return best detection
     *
     * @param bitmap Input image (any size, will be resized)
     * @return DetectionResult with bounding box and confidence, or null if no plate detected
     */
    fun detect(bitmap: Bitmap): DetectionResult? {
        if (!isInitialized) {
            initialize()
        }

        return try {
            val originalWidth = bitmap.width
            val originalHeight = bitmap.height

            // Step 1: Preprocess image to model input format
            val inputTensor = preprocessImage(bitmap)

            // Step 2: Run inference
            val inputs = mapOf(ortSession!!.inputNames.iterator().next() to inputTensor)
            val outputs = ortSession!!.run(inputs)

            // Step 3: Parse output tensor
            val outputTensor = outputs[0].value
            val detections = parseDetections(outputTensor, originalWidth, originalHeight)

            // Clean up
            inputTensor.close()
            outputs.close()

            // Step 4: Apply NMS and return best detection
            if (detections.isNotEmpty()) {
                applyNMS(detections).firstOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("PlateDetector", "Detection failed", e)
            null
        }
    }

    /**
     * Preprocesses the input bitmap for YOLO ONNX model.
     *
     * Converts from:
     * - Android Bitmap (any size, ARGB)
     * To:
     * - ONNX Tensor [1, 3, 640, 640] (NCHW format, RGB, normalized [0,1])
     *
     * Format: Channels-first (CHW) - required by YOLO ONNX model
     */
    private fun preprocessImage(bitmap: Bitmap): OnnxTensor {
        // Resize to 640x640
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

        // Allocate buffer for [1, 3, 640, 640] tensor
        val buffer = FloatBuffer.allocate(1 * 3 * INPUT_SIZE * INPUT_SIZE)

        // Extract pixels
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // Convert to CHW format (channels-first) and normalize to [0, 1]
        // Layout: [R_channel_all_pixels, G_channel_all_pixels, B_channel_all_pixels]
        val rChannel = FloatArray(INPUT_SIZE * INPUT_SIZE)
        val gChannel = FloatArray(INPUT_SIZE * INPUT_SIZE)
        val bChannel = FloatArray(INPUT_SIZE * INPUT_SIZE)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            rChannel[i] = ((pixel shr 16) and 0xFF) / 255.0f
            gChannel[i] = ((pixel shr 8) and 0xFF) / 255.0f
            bChannel[i] = (pixel and 0xFF) / 255.0f
        }

        // Put in CHW order: R, then G, then B
        buffer.put(rChannel)
        buffer.put(gChannel)
        buffer.put(bChannel)
        buffer.rewind()

        // Create ONNX tensor with shape [1, 3, 640, 640]
        val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        return OnnxTensor.createTensor(ortEnvironment, buffer, shape)
    }

    /**
     * Parses YOLO ONNX output tensor into detection objects.
     *
     * Input format: [1, 5, 8400]
     *   - Dimension 0: Batch (always 1)
     *   - Dimension 1: [x_center, y_center, width, height, confidence]
     *   - Dimension 2: 8400 anchor points
     *
     * Coordinates are normalized (0-1 range), need to scale to original image size.
     */
    private fun parseDetections(
        output: Any,
        originalWidth: Int,
        originalHeight: Int
    ): List<DetectionResult> {
        val detections = mutableListOf<DetectionResult>()

        try {
            // Output shape: [1, 5, 8400]
            val outputArray = output as Array<*>
            android.util.Log.d("PlateDetector", "Parsing output array of size: ${outputArray.size}")

            val batch = outputArray[0] as Array<*>  // [5, 8400]
            android.util.Log.d("PlateDetector", "Batch size: ${batch.size}")

            // Extract the 5 rows: [x, y, w, h, conf]
            val xCenters = batch[0] as FloatArray   // 8400 values
            val yCenters = batch[1] as FloatArray   // 8400 values
            val widths = batch[2] as FloatArray     // 8400 values
            val heights = batch[3] as FloatArray    // 8400 values
            val confidences = batch[4] as FloatArray // 8400 values

            android.util.Log.d("PlateDetector", "Extracted arrays - xCenters: ${xCenters.size}, confidences: ${confidences.size}")

        // Process each of the 8400 anchor points
        for (i in 0 until NUM_ANCHORS) {
            val confidence = confidences[i]

            // Filter by confidence threshold
            if (confidence < CONFIDENCE_THRESHOLD) continue

            // Coordinates are in 640x640 space (model input size), not normalized
            // Need to scale to original image dimensions
            val xCenter640 = xCenters[i]
            val yCenter640 = yCenters[i]
            val width640 = widths[i]
            val height640 = heights[i]

            // Scale from 640x640 to original image size
            val scaleX = originalWidth / 640f
            val scaleY = originalHeight / 640f

            val xCenter = xCenter640 * scaleX
            val yCenter = yCenter640 * scaleY
            val width = width640 * scaleX
            val height = height640 * scaleY

            // Convert center coordinates to corner coordinates (left, top, right, bottom)
            val left = (xCenter - width / 2f).coerceAtLeast(0f)
            val top = (yCenter - height / 2f).coerceAtLeast(0f)
            val right = (xCenter + width / 2f).coerceAtMost(originalWidth.toFloat())
            val bottom = (yCenter + height / 2f).coerceAtMost(originalHeight.toFloat())

            val boundingBox = RectF(left, top, right, bottom)

                detections.add(
                    DetectionResult(
                        boundingBox = boundingBox,
                        confidence = confidence,
                        imageWidth = originalWidth,
                        imageHeight = originalHeight
                    )
                )
            }

            android.util.Log.d("PlateDetector", "Found ${detections.size} detections above threshold")
        } catch (e: Exception) {
            android.util.Log.e("PlateDetector", "Error parsing detections", e)
        }

        return detections
    }

    /**
     * Applies Non-Maximum Suppression (NMS) to remove overlapping detections.
     *
     * Algorithm:
     * 1. Sort detections by confidence (descending)
     * 2. Keep highest confidence detection
     * 3. Remove all detections that overlap significantly (IoU > threshold)
     * 4. Repeat for remaining detections
     *
     * @return List of non-overlapping detections, sorted by confidence (highest first)
     */
    private fun applyNMS(detections: List<DetectionResult>): List<DetectionResult> {
        if (detections.isEmpty()) return emptyList()

        // Sort by confidence (descending)
        val sorted = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<DetectionResult>()

        for (detection in sorted) {
            var shouldAdd = true

            // Check IoU with already selected detections
            for (selectedDetection in selected) {
                val iou = calculateIoU(detection.boundingBox, selectedDetection.boundingBox)
                if (iou > IOU_THRESHOLD) {
                    shouldAdd = false
                    break
                }
            }

            if (shouldAdd) {
                selected.add(detection)
            }
        }

        return selected
    }

    /**
     * Calculates Intersection over Union (IoU) between two bounding boxes.
     *
     * IoU = (Area of Intersection) / (Area of Union)
     *
     * Used to determine if two detections are overlapping the same object.
     * Values closer to 1.0 indicate high overlap.
     */
    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        // Calculate intersection rectangle
        val intersectionLeft = max(box1.left, box2.left)
        val intersectionTop = max(box1.top, box2.top)
        val intersectionRight = min(box1.right, box2.right)
        val intersectionBottom = min(box1.bottom, box2.bottom)

        // Check if boxes don't intersect
        if (intersectionRight < intersectionLeft || intersectionBottom < intersectionTop) {
            return 0f
        }

        // Calculate areas
        val intersectionArea = (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
        val box1Area = box1.width() * box1.height()
        val box2Area = box2.width() * box2.height()
        val unionArea = box1Area + box2Area - intersectionArea

        return intersectionArea / unionArea
    }

    /**
     * Releases ONNX Runtime resources.
     * Call this when done (e.g., in onDestroy or use with `use { }` block).
     */
    override fun close() {
        ortSession?.close()
        ortSession = null
        isInitialized = false
    }
}
