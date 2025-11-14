package com.example.plateocr.ui.screen

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.exifinterface.media.ExifInterface
import com.example.plateocr.data.model.OcrResult
import com.example.plateocr.data.model.VehicleInfo
import com.example.plateocr.data.model.gov.AggregateVehicleData
import com.example.plateocr.data.repository.VehicleRepository
import com.example.plateocr.ml.detector.PlateDetector
import com.example.plateocr.ml.ocr.OcrEngine
import com.example.plateocr.ui.components.FieldTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Simple camera screen that uses the system camera to capture images.
 *
 * Flow:
 * 1. User taps "Take Picture" button
 * 2. System camera app opens
 * 3. User takes photo
 * 4. Photo is processed through YOLO + OCR pipeline
 * 5. Results are displayed
 */
@Composable
fun CameraScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var croppedPlateBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var ocrResult by remember { mutableStateOf<OcrResult?>(null) }
    var vehicleData by remember { mutableStateOf<AggregateVehicleData?>(null) }
    var selectedTab by remember { mutableStateOf(0) }  // 0 = Detection, 1 = Vehicle Details
    var isProcessing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var detectionConfidence by remember { mutableStateOf<Float?>(null) }
    var manualPlateNumber by remember { mutableStateOf("") }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PermissionChecker.PERMISSION_GRANTED
        )
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            error = "Camera permission is required to take pictures"
        }
    }

    // Create temporary file for camera output
    val photoFile = remember {
        File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
    }
    val photoUri = remember {
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }

    // Camera launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            scope.launch {
                isProcessing = true
                error = null
                // Clear old data immediately to avoid showing stale results
                vehicleData = null
                ocrResult = null
                croppedPlateBitmap = null
                detectionConfidence = null
                selectedTab = 0

                try {
                    // Load bitmap from file with correct orientation
                    val bitmap = withContext(Dispatchers.IO) {
                        val rawBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                        rawBitmap?.let { fixImageRotation(it, photoFile.absolutePath) }
                    }

                    if (bitmap != null) {
                        capturedBitmap = bitmap

                        // Process with detector and OCR
                        withContext(Dispatchers.IO) {
                            val detector = PlateDetector(context)
                            val ocrEngine = OcrEngine()

                            try {
                                // Detect plate
                                android.util.Log.d("CameraScreen", "Running detection on ${bitmap.width}x${bitmap.height} image")
                                val detectionResult = detector.detect(bitmap)

                                if (detectionResult != null) {
                                    android.util.Log.d("CameraScreen", "Detection succeeded: confidence=${detectionResult.confidence}, bbox=${detectionResult.boundingBox}")
                                    detectionConfidence = detectionResult.confidence

                                    // Crop detected region
                                    val croppedBitmap = cropBitmap(bitmap, detectionResult.boundingBox)
                                    android.util.Log.d("CameraScreen", "Cropped plate: ${croppedBitmap.width}x${croppedBitmap.height}")

                                    // Save cropped plate for display
                                    croppedPlateBitmap = croppedBitmap

                                    // Run OCR
                                    ocrResult = ocrEngine.recognizeText(croppedBitmap)
                                } else {
                                    // Detection failed - try OCR on full image as fallback
                                    android.util.Log.w("CameraScreen", "Detection failed, trying OCR on full image")
                                    ocrResult = ocrEngine.recognizeText(bitmap)

                                    if (ocrResult?.isValid == true) {
                                        android.util.Log.d("CameraScreen", "Fallback OCR succeeded: ${ocrResult?.cleanedText}")
                                    } else {
                                        error = "No license plate detected or recognized"
                                    }
                                }

                                // OCR processing complete - show results immediately
                                isProcessing = false

                                // If OCR was successful, auto-fill manual input and lookup comprehensive vehicle data in background
                                ocrResult?.let { ocr ->
                                    if (ocr.isValid) {
                                        // Auto-fill the manual plate number field
                                        manualPlateNumber = ocr.cleanedText

                                        // Launch vehicle lookup in separate coroutine for progressive loading
                                        scope.launch {
                                            try {
                                                val repository = VehicleRepository()
                                                // Collect progressive updates from the flow
                                                var isFirstUpdate = true
                                                repository.lookupComprehensiveData(ocr.cleanedText).collectLatest { data ->
                                                    vehicleData = data
                                                    // Auto-switch to details tab when first vehicle data arrives
                                                    if (data.vehicle != null && isFirstUpdate) {
                                                        selectedTab = 1
                                                        isFirstUpdate = false
                                                    }
                                                    android.util.Log.d("CameraScreen", "Vehicle data updated: ${data.getLoadedSectionsCount()} sections loaded")
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("CameraScreen", "Error looking up vehicle info", e)
                                            }
                                        }
                                    }
                                }
                            } finally {
                                detector.close()
                                ocrEngine.close()
                            }
                        }
                    } else {
                        error = "Failed to load image"
                        isProcessing = false
                    }
                } catch (e: Exception) {
                    error = "Error: ${e.message}"
                    isProcessing = false
                }
            }
        }
    }

    // UI
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "License Plate Scanner",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Take Picture Button
        Button(
            onClick = {
                if (hasCameraPermission) {
                    takePictureLauncher.launch(photoUri)
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isProcessing) "Processing..."
                else if (hasCameraPermission) "Take Picture"
                else "Grant Camera Permission"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Divider with "OR" text
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = "OR",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Manual entry section
        OutlinedTextField(
            value = manualPlateNumber,
            onValueChange = { newValue ->
                // Only allow digits and limit to 8 characters
                if (newValue.all { it.isDigit() } && newValue.length <= 8) {
                    manualPlateNumber = newValue
                }
            },
            label = { Text("Enter Plate Number") },
            placeholder = { Text("7-8 digits") },
            enabled = !isProcessing,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (manualPlateNumber.length in 7..8) {
                    // Clear previous results
                    capturedBitmap = null
                    croppedPlateBitmap = null
                    ocrResult = null
                    vehicleData = null
                    detectionConfidence = null
                    error = null
                    isProcessing = true
                    selectedTab = 1  // Switch to vehicle details tab

                    scope.launch {
                        try {
                            val repository = VehicleRepository()
                            var isFirstUpdate = true
                            // Collect progressive updates from the flow
                            repository.lookupComprehensiveData(manualPlateNumber).collectLatest { data ->
                                vehicleData = data

                                // Stop processing spinner once we get first vehicle data
                                if (data.vehicle != null && isFirstUpdate) {
                                    isProcessing = false
                                    isFirstUpdate = false
                                }

                                // If fully loaded but no vehicle found
                                if (data.vehicle == null && data.isFullyLoaded()) {
                                    error = "Vehicle not found"
                                    isProcessing = false
                                }
                            }
                        } catch (e: Exception) {
                            error = "Error: ${e.message}"
                            isProcessing = false
                        }
                    }
                } else {
                    error = "Please enter a valid plate number (7-8 digits)"
                }
            },
            enabled = !isProcessing && manualPlateNumber.length in 7..8,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Lookup Vehicle")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Processing indicator
        if (isProcessing) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Error message
        error?.let { errorMsg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    errorMsg,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Results with tabs
        if ((capturedBitmap != null || vehicleData != null) && !isProcessing) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Detection") },
                    enabled = capturedBitmap != null  // Only enable if we have camera results
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Vehicle Details") },
                    enabled = vehicleData?.vehicle != null
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tab content
            when (selectedTab) {
                0 -> {
                    // DETECTION TAB
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Detection confidence
                            detectionConfidence?.let { conf ->
                                Text(
                                    "Detection Confidence: ${(conf * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // OCR results
                            ocrResult?.let { ocr ->
                                Text(
                                    "License Plate:",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    ocr.cleanedText,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    "Raw OCR: ${ocr.rawText}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )

                                Text(
                                    "OCR Confidence: ${(ocr.confidence * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                Text(
                                    "Valid Format: ${if (ocr.isValid) "✓ Yes" else "✗ No"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Show cropped plate detection
                            croppedPlateBitmap?.let { croppedPlate ->
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Detected Plate Region:",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Image(
                                    bitmap = croppedPlate.asImageBitmap(),
                                    contentDescription = "Cropped license plate",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Show full captured image
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Full Image:",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Image(
                                bitmap = capturedBitmap!!.asImageBitmap(),
                                contentDescription = "Captured image",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                1 -> {
                    // VEHICLE DETAILS TAB
                    VehicleDetailsTab(vehicleData)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Try Again button
            OutlinedButton(
                onClick = {
                    capturedBitmap = null
                    croppedPlateBitmap = null
                    ocrResult = null
                    vehicleData = null
                    selectedTab = 0
                    error = null
                    detectionConfidence = null
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Take Another Picture")
            }
        }
    }
}

// VehicleDetailsTab is now in VehicleDetailsCollapsible.kt

/**
 * Fixes image rotation based on EXIF orientation data.
 */
private fun fixImageRotation(bitmap: Bitmap, imagePath: String): Bitmap {
    val exif = ExifInterface(imagePath)
    val orientation = exif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        else -> return bitmap // No rotation needed
    }

    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/**
 * Crops a bitmap to the specified bounding box.
 */
private fun cropBitmap(bitmap: Bitmap, bbox: android.graphics.RectF): Bitmap {
    val left = bbox.left.toInt().coerceIn(0, bitmap.width - 1)
    val top = bbox.top.toInt().coerceIn(0, bitmap.height - 1)
    val right = bbox.right.toInt().coerceIn(0, bitmap.width)
    val bottom = bbox.bottom.toInt().coerceIn(0, bitmap.height)

    val width = (right - left).coerceAtLeast(1)
    val height = (bottom - top).coerceAtLeast(1)

    return Bitmap.createBitmap(bitmap, left, top, width, height)
}
