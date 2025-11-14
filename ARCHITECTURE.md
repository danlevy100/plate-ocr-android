# Architecture Documentation

## Overview

This Android app performs Israeli license plate recognition using a pipeline of machine learning models and backend API integration. The architecture follows **MVVM (Model-View-ViewModel)** pattern with clean separation of concerns.

## Project Structure

```
app/src/main/java/com/example/plateocr/
├── data/                       # Data layer
│   ├── api/                    # Network API clients
│   │   ├── ApiClient.kt        # Retrofit singleton
│   │   └── VehicleApiService.kt # Backend API interface
│   ├── model/                  # Data models
│   │   ├── DetectionResult.kt  # YOLO output
│   │   ├── OcrResult.kt        # OCR output
│   │   ├── VehicleInfo.kt      # API response
│   │   └── PlateRecognitionResult.kt # Combined result
│   └── repository/             # Data coordination
│       └── PlateRecognitionRepository.kt # Main repository
├── ml/                         # Machine learning models
│   ├── detector/
│   │   └── PlateDetector.kt    # YOLO TFLite detector
│   └── ocr/
│       └── OcrEngine.kt        # Text extraction
├── ui/                         # UI layer (TODO)
│   ├── screen/                 # Compose screens
│   ├── component/              # Reusable UI components
│   ├── theme/                  # Material theme
│   └── viewmodel/              # ViewModels
├── util/                       # Utilities
│   └── PlateValidator.kt       # Israeli plate validation
└── MainActivity.kt             # Entry point
```

## Recognition Pipeline

The plate recognition follows this flow:

```
Camera Image (Bitmap)
    ↓
[1] PlateDetector.detect()
    ↓ (DetectionResult: bounding box, confidence)
Crop to detected region
    ↓
[2] OcrEngine.extractText()
    ↓ (rawText: string)
[3] PlateValidator.validate()
    ↓ (OcrResult: cleaned text, validation)
[4] VehicleApiService.lookupVehicle()
    ↓ (VehicleInfo: vehicle details)
PlateRecognitionResult
```

### Step-by-Step Breakdown

#### 1. Detection (YOLO)
- **Input**: Full camera frame (Bitmap)
- **Model**: TensorFlow Lite (.tflite converted from .pt)
- **Output**: Bounding box + confidence score
- **Location**: `ml/detector/PlateDetector.kt`

#### 2. OCR (Text Extraction)
- **Input**: Cropped plate region (Bitmap)
- **Model**: ML Kit or PaddleOCR Lite
- **Output**: Raw text string
- **Location**: `ml/ocr/OcrEngine.kt`

#### 3. Validation
- **Input**: Raw OCR text
- **Process**: Clean, format, validate Israeli plate rules
- **Output**: Validated plate number + confidence
- **Location**: `util/PlateValidator.kt`

#### 4. Vehicle Lookup
- **Input**: Validated plate number
- **API**: Backend service (yad2scraper)
- **Output**: Vehicle information
- **Location**: `data/api/VehicleApiService.kt`

## Data Models

### DetectionResult
```kotlin
data class DetectionResult(
    val boundingBox: RectF,     // Plate location in image
    val confidence: Float,       // 0.0-1.0 detection confidence
    val imageWidth: Int,         // Source image dimensions
    val imageHeight: Int
)
```

### OcrResult
```kotlin
data class OcrResult(
    val rawText: String,         // Raw OCR output
    val cleanedText: String,     // Validated plate number
    val confidence: Float,       // OCR confidence
    val isValid: Boolean         // Passes Israeli format validation
)
```

### VehicleInfo
```kotlin
data class VehicleInfo(
    val plateNumber: String,
    val manufacturer: String?,
    val model: String?,
    val year: Int?,
    val color: String?,
    val testExpiry: String?,
    // ... more fields
)
```

### PlateRecognitionResult
```kotlin
data class PlateRecognitionResult(
    val detectionResult: DetectionResult?,
    val ocrResult: OcrResult?,
    val vehicleInfo: VehicleInfo?,
    val processingTimeMs: Long
)
```

## Israeli Plate Validation

Israeli license plates follow specific formats:
- **7 digits**: `XXX-XX-XXX` (old format)
- **8 digits**: `XXX-XX-XXXX` (new format)

The `PlateValidator` handles:
- Text cleaning (remove spaces, dashes, non-numeric)
- Format validation (7-8 digits only)
- Confidence scoring based on OCR quality
- Multi-candidate selection (best result from multiple OCR attempts)

Example validation:
```kotlin
val (isValid, confidence) = PlateValidator.validateWithConfidence("123-45-678")
// isValid = true, confidence = 90 (high quality)

val cleaned = PlateValidator.cleanPlateText("123 45 678")
// cleaned = "12345678"

val formatted = PlateValidator.formatPlate("12345678")
// formatted = "123-45-6789"
```

## MVVM Architecture

### Repository Pattern
`PlateRecognitionRepository` is the single source of truth:
- Coordinates ML models and API calls
- Handles error cases gracefully
- Returns `Result<PlateRecognitionResult>`
- Runs on IO dispatcher (background thread)

### Future: ViewModel (TODO)
```kotlin
class PlateRecognitionViewModel(
    private val repository: PlateRecognitionRepository
) : ViewModel() {

    val recognitionResult = MutableStateFlow<PlateRecognitionResult?>(null)

    fun processImage(bitmap: Bitmap) {
        viewModelScope.launch {
            repository.recognizePlate(bitmap)
                .onSuccess { result ->
                    recognitionResult.value = result
                }
        }
    }
}
```

### Future: Compose UI (TODO)
```kotlin
@Composable
fun PlateRecognitionScreen(viewModel: PlateRecognitionViewModel) {
    // Camera preview with CameraX
    // Show detection overlay
    // Display results
}
```

## Technology Stack

### Core
- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0)
- **Architecture**: MVVM + Repository pattern

### ML & Camera
- **Camera**: CameraX (androidx.camera:*)
- **YOLO Detection**: TensorFlow Lite (org.tensorflow:tensorflow-lite)
- **OCR**: ML Kit or PaddleOCR Lite (TBD)

### Networking
- **HTTP Client**: Retrofit + OkHttp
- **JSON Parsing**: Moshi with Kotlin codegen
- **Logging**: OkHttp Logging Interceptor

### UI
- **Framework**: Jetpack Compose
- **Theme**: Material 3
- **Image Loading**: Coil

## Next Steps

### 1. Model Conversion (High Priority)
Convert YOLO .pt models to TensorFlow Lite:

```bash
# On WSL (yad2scraper environment)
cd /home/dan/yad2scraper

# Export YOLO to ONNX
python -m ultralytics export --weights yad2_scraper/yolo_runs/lp_clean_96_rect/weights/best.pt --format onnx

# Convert ONNX to TFLite
pip install tf2onnx tensorflow
python -m tf2onnx.convert --opset 13 --tflite output.tflite --input best.onnx

# Copy to Android assets
cp output.tflite /mnt/d/Projects/plate-ocr-android/app/src/main/assets/yolo_plate_detector.tflite
```

**Model Choice**:
- Start with `lp_clean_96_rect` (5.5MB) for faster inference
- Benchmark accuracy on test images
- Upgrade to `lp_qwen_reviewed_aug` (57MB) if needed

### 2. Implement PlateDetector (High Priority)
- Load TFLite model from assets
- Implement preprocessing (resize, normalize)
- Run inference and parse outputs
- Apply NMS (Non-Maximum Suppression)
- Return DetectionResult

### 3. Implement OcrEngine (High Priority)
**Option A: ML Kit (Recommended for MVP)**
```kotlin
// Add dependency
implementation("com.google.mlkit:text-recognition:16.0.0")

// Use in OcrEngine
val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
val image = InputImage.fromBitmap(bitmap, 0)
val result = recognizer.process(image).await()
return result.text
```

**Option B: PaddleOCR (Better Accuracy)**
- Port PaddleOCR C++ libs to Android
- Integrate following: https://github.com/PaddlePaddle/PaddleOCR/tree/main/deploy/android_demo

### 4. CameraX Integration (Medium Priority)
- Implement camera preview
- Capture frames at regular intervals
- Process with pipeline
- Show bounding box overlay

### 5. Backend API (Low Priority)
- Expose yad2scraper as REST API (FastAPI)
- Deploy on accessible server
- Update `VehicleApiService.BASE_URL`

### 6. UI Development (Medium Priority)
- Camera screen with live preview
- Detection overlay (bounding box)
- Results screen (plate + vehicle info)
- Loading states and error handling

## Testing Strategy

### Unit Tests
- `PlateValidatorTest`: Test validation logic with edge cases
- `RepositoryTest`: Mock ML models and API, test pipeline

### Instrumented Tests
- Camera integration tests
- ML model inference tests (with sample images)

### Test Dataset
Reuse test images from yad2scraper:
- Copy sample plates to `app/src/androidTest/assets/`
- Test against ground truth labels
- Measure accuracy metrics

## Performance Considerations

### Model Optimization
- Use TFLite GPU delegate for faster inference
- Consider model quantization (reduces size, slight accuracy loss)
- Benchmark: Target <200ms per frame on mid-range device

### Memory Management
- Recycle bitmaps after processing
- Use appropriate image resolutions
- Close ML model resources properly

### Network
- Cache vehicle lookups
- Handle offline gracefully
- Show cached results while loading

## Troubleshooting

### Common Issues

**Gradle sync fails**
- Check internet connection for dependency downloads
- Invalidate caches: File → Invalidate Caches / Restart

**TFLite model not found**
- Ensure model is in `app/src/main/assets/`
- Check model filename matches `PlateDetector.MODEL_FILE`

**OCR accuracy low**
- Ensure cropped region is clean (good bounding box)
- Try preprocessing: grayscale, contrast adjustment, denoising
- Consider PaddleOCR if ML Kit insufficient

**API connection failed**
- Emulator: Use `10.0.2.2` instead of `localhost`
- Physical device: Use computer's IP address
- Check backend is running and accessible

## Resources

- [Ultralytics YOLO Export](https://docs.ultralytics.com/modes/export/)
- [TensorFlow Lite Android](https://www.tensorflow.org/lite/android)
- [CameraX Documentation](https://developer.android.com/training/camerax)
- [ML Kit Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition)
- [PaddleOCR Android Demo](https://github.com/PaddlePaddle/PaddleOCR/tree/main/deploy/android_demo)
