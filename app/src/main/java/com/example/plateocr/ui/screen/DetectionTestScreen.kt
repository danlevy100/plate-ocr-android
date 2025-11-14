package com.example.plateocr.ui.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.plateocr.R
import com.example.plateocr.ml.detector.PlateDetector
import com.example.plateocr.ml.ocr.OcrEngine
import com.example.plateocr.data.model.OcrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Test screen to visualize YOLO detections with bounding boxes.
 *
 * Shows test images with detection results overlaid.
 * Helps debug detection issues visually.
 */
@Composable
fun DetectionTestScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var testResults by remember { mutableStateOf<List<TestResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "YOLO Detection Test",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    error = null

                    try {
                        testResults = withContext(Dispatchers.IO) {
                            runDetectionTests(context)
                        }
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Testing..." else "Run Detection on All Test Images")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (error != null) {
            Text(
                "Error: $error",
                color = MaterialTheme.colorScheme.error
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        testResults.forEach { result ->
            TestResultCard(result)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun TestResultCard(result: TestResult) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                result.imageName,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (result.detected) {
                Text(
                    "✅ Detected - Confidence: ${(result.confidence * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.primary
                )

                result.boundingBox?.let { bbox ->
                    Text(
                        "Box: (${bbox.left.toInt()}, ${bbox.top.toInt()}) to (${bbox.right.toInt()}, ${bbox.bottom.toInt()})",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Show OCR results
                result.ocrResult?.let { ocr ->
                    // Show formatted plate number prominently
                    Text(
                        "🚗 ${ocr.cleanedText}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Raw OCR: ${ocr.rawText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "Confidence: ${(ocr.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                } ?: run {
                    Text(
                        "❌ OCR failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Text(
                    "❌ No detection",
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Show image with bounding box
            result.imageWithBox?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Detection result for ${result.imageName}",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

data class TestResult(
    val imageName: String,
    val detected: Boolean,
    val confidence: Float,
    val boundingBox: android.graphics.RectF?,
    val imageWithBox: Bitmap?,
    val ocrResult: OcrResult? = null
)

/**
 * Runs detection on all test images and draws bounding boxes.
 */
private suspend fun runDetectionTests(context: android.content.Context): List<TestResult> {
    val results = mutableListOf<TestResult>()
    val detector = PlateDetector(context)
    val ocrEngine = OcrEngine()

    // Test images in res/drawable (all 8 test plates)
    val testImages = listOf(
        R.drawable.test_plate_1 to "test_plate_1.jpg",
        R.drawable.test_plate_2 to "test_plate_2.jpg",
        R.drawable.test_plate_3 to "test_plate_3.jpg",
        R.drawable.test_plate_4 to "test_plate_4.jpg",
        R.drawable.test_plate_5 to "test_plate_5.jpg",
        R.drawable.test_plate_6 to "test_plate_6.jpg",
        R.drawable.test_plate_7 to "test_plate_7.jpg",
        R.drawable.test_plate_8 to "test_plate_8.jpg"
    )

    for ((drawableId, name) in testImages) {
        try {
            // Load original bitmap
            val originalBitmap = BitmapFactory.decodeResource(
                context.resources,
                drawableId
            )

            // Run detection
            val detectionResult = detector.detect(originalBitmap)

            android.util.Log.d("DetectionTest", "Image: $name, Detection: ${detectionResult != null}")
            if (detectionResult != null) {
                android.util.Log.d("DetectionTest", "  Confidence: ${detectionResult.confidence}")
                android.util.Log.d("DetectionTest", "  BBox: ${detectionResult.boundingBox}")
                android.util.Log.d("DetectionTest", "  BBox width: ${detectionResult.boundingBox.width()}, height: ${detectionResult.boundingBox.height()}")

                // Downsample image for display (to avoid OutOfMemoryError)
                val displayBitmap = downsampleBitmap(originalBitmap, 1080)
                val scale = displayBitmap.width.toFloat() / originalBitmap.width.toFloat()

                // Scale bounding box to match downsampled image
                val scaledBbox = android.graphics.RectF(
                    detectionResult.boundingBox.left * scale,
                    detectionResult.boundingBox.top * scale,
                    detectionResult.boundingBox.right * scale,
                    detectionResult.boundingBox.bottom * scale
                )

                // Draw bounding box on downsampled image
                val imageWithBox = drawBoundingBox(
                    displayBitmap.copy(Bitmap.Config.ARGB_8888, true),
                    scaledBbox,
                    detectionResult.confidence
                )

                // Crop the detected plate region from ORIGINAL image for OCR
                val croppedPlate = cropBitmap(
                    originalBitmap,
                    detectionResult.boundingBox
                )

                // Run OCR on cropped plate
                val ocrResult = ocrEngine.recognizeText(croppedPlate)

                results.add(
                    TestResult(
                        imageName = name,
                        detected = true,
                        confidence = detectionResult.confidence,
                        boundingBox = detectionResult.boundingBox,
                        imageWithBox = imageWithBox,
                        ocrResult = ocrResult
                    )
                )
            } else {
                // Downsample even for failed detections
                val displayBitmap = downsampleBitmap(originalBitmap, 1080)

                results.add(
                    TestResult(
                        imageName = name,
                        detected = false,
                        confidence = 0f,
                        boundingBox = null,
                        imageWithBox = displayBitmap
                    )
                )
            }
        } catch (e: Exception) {
            results.add(
                TestResult(
                    imageName = name,
                    detected = false,
                    confidence = 0f,
                    boundingBox = null,
                    imageWithBox = null
                )
            )
        }
    }

    detector.close()
    ocrEngine.close()
    return results
}

/**
 * Draws a bounding box on the bitmap.
 */
private fun drawBoundingBox(
    bitmap: Bitmap,
    bbox: android.graphics.RectF,
    confidence: Float
): Bitmap {
    android.util.Log.d("DrawBoundingBox", "Drawing bbox: $bbox on bitmap ${bitmap.width}x${bitmap.height}")

    // Validate bounding box
    if (bbox.width() <= 0 || bbox.height() <= 0) {
        android.util.Log.e("DrawBoundingBox", "Invalid bbox dimensions: width=${bbox.width()}, height=${bbox.height()}")
        return bitmap
    }

    val canvas = Canvas(bitmap)

    // Green box with thickness based on confidence
    val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = android.graphics.Color.GREEN
        alpha = (confidence * 255).toInt().coerceIn(100, 255)
    }

    // Draw rectangle
    canvas.drawRect(bbox, paint)

    // Draw confidence text
    val textPaint = Paint().apply {
        color = android.graphics.Color.GREEN
        textSize = 60f
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    val confidenceText = "${(confidence * 100).toInt()}%"
    canvas.drawText(
        confidenceText,
        bbox.left + 10f,
        bbox.top - 10f,
        textPaint
    )

    return bitmap
}

/**
 * Downsamples a bitmap to a maximum width while maintaining aspect ratio.
 * Prevents OutOfMemoryError when displaying large images.
 */
private fun downsampleBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
    if (bitmap.width <= maxWidth) {
        return bitmap
    }

    val scale = maxWidth.toFloat() / bitmap.width.toFloat()
    val newHeight = (bitmap.height * scale).toInt()

    android.util.Log.d("Downsample", "Downsampling ${bitmap.width}x${bitmap.height} to ${maxWidth}x${newHeight}")

    return Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
}

/**
 * Crops a bitmap to the specified bounding box.
 * Used to extract the license plate region for OCR.
 */
private fun cropBitmap(bitmap: Bitmap, bbox: android.graphics.RectF): Bitmap {
    // Ensure coordinates are within bitmap bounds
    val left = bbox.left.toInt().coerceIn(0, bitmap.width - 1)
    val top = bbox.top.toInt().coerceIn(0, bitmap.height - 1)
    val right = bbox.right.toInt().coerceIn(0, bitmap.width)
    val bottom = bbox.bottom.toInt().coerceIn(0, bitmap.height)

    val width = (right - left).coerceAtLeast(1)
    val height = (bottom - top).coerceAtLeast(1)

    android.util.Log.d("CropBitmap", "Cropping at ($left, $top) size ${width}x${height}")

    return Bitmap.createBitmap(bitmap, left, top, width, height)
}
