# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Plate OCR is an Android application for Israeli license plate recognition, built using Kotlin and Jetpack Compose. The project follows MVVM architecture with a complete ML pipeline integrating YOLO detection, ML Kit OCR, and Israeli government vehicle data APIs.

## Build System

This project uses Gradle with Kotlin DSL (`.gradle.kts`). Dependencies are managed using the version catalog pattern in `gradle/libs.versions.toml`.

### Common Commands

**Build the app:**
```bash
./gradlew build
```

**Build debug APK:**
```bash
./gradlew assembleDebug
```

**Build release APK:**
```bash
./gradlew assembleRelease
```

**Run unit tests:**
```bash
./gradlew test
```

**Run instrumented tests (requires emulator/device):**
```bash
./gradlew connectedAndroidTest
```

**Run a specific test class:**
```bash
./gradlew test --tests com.example.plateocr.ExampleUnitTest
```

**Clean build:**
```bash
./gradlew clean
```

**Install debug build on connected device:**
```bash
./gradlew installDebug
```

## Architecture Overview

The app implements a complete ML pipeline following the **Repository pattern in MVVM**:

### Recognition Pipeline Flow

```
Camera Image (Bitmap)
    ↓
[1] PlateDetector.detect() - YOLO ONNX model (640x640 input)
    ↓ (DetectionResult: bounding box, confidence)
[2] Repository crops to detected region
    ↓
[3] OcrEngine.recognizeText() - ML Kit Text Recognition
    ↓ (rawText: string)
[4] PlateValidator.validate() - Validates Israeli format
    ↓ (OcrResult: cleaned text, validation)
[5] VehicleApiService.lookupVehicle() - Israeli gov API
    ↓ (VehicleInfo: vehicle details)
PlateRecognitionResult
```

### Key Components

**Repository Layer:**
- `PlateRecognitionRepository` - Coordinates entire pipeline (main entry point)
- `VehicleRepository` - Handles government API calls for vehicle data

**ML Layer:**
- `ml/detector/PlateDetector.kt` - YOLO detection using ONNX Runtime (11 MB model)
- `ml/ocr/OcrEngine.kt` - Text extraction using ML Kit Text Recognition

**Data Models:**
- `DetectionResult` - YOLO output (bounding box, confidence)
- `OcrResult` - OCR output (raw text, cleaned text, validation)
- `VehicleInfo` - Vehicle data from government API
- `PlateRecognitionResult` - Complete pipeline result
- `data/model/gov/*` - Government API response models (comprehensive vehicle data)

**Validation:**
- `util/PlateValidator.kt` - Israeli plate format validation (7-8 digits)

**UI Layer:**
- `ui/screen/CameraScreen.kt` - Live camera capture with CameraX
- `ui/screen/DetectionTestScreen.kt` - Static image testing interface
- `ui/screen/VehicleDetailsCollapsible.kt` - Collapsible vehicle info display
- `MainActivity.kt` - Single activity with Compose

### Israeli Plate Formats

Valid formats (all numeric, no letters):
- **7 digits**: `XXX-XX-XXX` (old format, e.g., `123-45-678`)
- **8 digits**: `XXX-XX-XXXX` (new format, e.g., `123-45-6789`)

The `PlateValidator` handles cleaning, formatting, and validation automatically.

## Project Structure

```
app/src/main/java/com/example/plateocr/
├── data/
│   ├── api/
│   │   ├── ApiClient.kt              # Retrofit singleton
│   │   └── VehicleApiService.kt      # Government API interface
│   ├── model/
│   │   ├── DetectionResult.kt        # YOLO output
│   │   ├── OcrResult.kt              # OCR output
│   │   ├── VehicleInfo.kt            # Main vehicle data
│   │   ├── PlateRecognitionResult.kt # Complete pipeline result
│   │   └── gov/                      # Government API models
│   └── repository/
│       ├── PlateRecognitionRepository.kt  # Main pipeline coordinator
│       └── VehicleRepository.kt           # Vehicle data access
├── ml/
│   ├── detector/PlateDetector.kt     # YOLO ONNX detector
│   └── ocr/OcrEngine.kt              # ML Kit text recognition
├── ui/
│   ├── screen/
│   │   ├── CameraScreen.kt           # Live camera capture
│   │   ├── DetectionTestScreen.kt    # Static image testing
│   │   └── VehicleDetailsCollapsible.kt  # Vehicle info display
│   ├── components/                    # Reusable UI components
│   ├── utils/                         # UI utilities
│   └── theme/                         # Material 3 theme
├── util/
│   ├── PlateValidator.kt             # Israeli plate validation
│   ├── FieldTranslations.kt          # Hebrew field translations
│   └── FieldConfig.kt                # Field display configuration
└── MainActivity.kt                    # App entry point
```

## Technology Stack

**Core:**
- Language: Kotlin
- Min SDK: 26 (Android 8.0)
- Target/Compile SDK: 36
- Java Version: 11
- Architecture: MVVM + Repository pattern

**ML & Camera:**
- Camera: CameraX (androidx.camera:*)
- Detection: ONNX Runtime (com.microsoft.onnxruntime:onnxruntime-android:1.17.0)
- OCR: ML Kit Text Recognition (com.google.mlkit:text-recognition:16.0.0)

**Networking:**
- HTTP: Retrofit + OkHttp
- JSON: Moshi with Kotlin codegen
- Coroutines: kotlinx-coroutines-play-services (for ML Kit .await())

**UI:**
- Framework: Jetpack Compose
- Theme: Material 3
- Image Loading: Coil

## YOLO Model Details

**Model Location:** `app/src/main/assets/yolo_plate_detector.onnx` (11 MB)

**Source:** Trained in yad2scraper project on Israeli license plates
- WSL Path: `/home/dan/yad2scraper/runs/israeli_plates/train3/weights/best.pt`

**Input:**
- Shape: [1, 3, 640, 640]
- Format: NCHW (batch, channels, height, width)
- Type: Float32, RGB, normalized [0, 1]

**Output:**
- Shape: [1, 5, 8400]
- Format: [batch, (x, y, w, h, confidence), anchors]

**Configuration:**
- Confidence threshold: 0.5 (50%)
- NMS threshold: 0.45
- Expected inference time: 50-150ms on modern devices

**Reconversion (if needed):**
```python
# On WSL in yad2scraper environment
from ultralytics import YOLO
model = YOLO('runs/israeli_plates/train3/weights/best.pt')
model.export(format='onnx', imgsz=640)
```

## Testing

**Unit Tests:**
- Located in `app/src/test/java/com/example/plateocr/`
- Test runner: JUnit

**Instrumented Tests:**
- Located in `app/src/androidTest/java/com/example/plateocr/`
- Test runner: AndroidJUnitRunner
- Includes `PlateDetectorTest.kt` for YOLO model testing

**Testing with Static Images:**
1. Place test images in `app/src/main/assets/test_images/`
2. Switch MainActivity to use `DetectionTestScreen` instead of `CameraScreen`
3. Run app and select images to test detection + OCR pipeline

**Copy test images from yad2scraper (WSL):**
```bash
cp /home/dan/yad2scraper/path/to/images/*.jpg \
   /mnt/d/Projects/plate-ocr-android/app/src/main/assets/test_images/
```

## Adding Dependencies

Update `gradle/libs.versions.toml`:
1. Add version in `[versions]` section
2. Add library in `[libraries]` section
3. Reference in `app/build.gradle.kts` using `libs.` prefix

Example:
```toml
[versions]
newlib = "1.0.0"

[libraries]
newlib = { group = "com.example", name = "newlib", version.ref = "newlib" }
```

Then in `app/build.gradle.kts`:
```kotlin
implementation(libs.newlib)
```

## Important Implementation Notes

**Using the Repository:**
```kotlin
// Initialize once (e.g., in Application or ViewModel)
val repository = PlateRecognitionRepository(context)
repository.initialize()

// Process an image
val result = repository.recognizePlate(bitmap, lookupVehicle = true)
result.onSuccess { plateResult ->
    // plateResult contains detection, OCR, and vehicle info
}

// Clean up when done
repository.close()
```

**Model Resource Management:**
- Always call `repository.initialize()` before first use
- Always call `repository.close()` when shutting down
- PlateDetector and OcrEngine use native resources that need cleanup

**CameraX Integration:**
- CameraScreen already implements live capture
- Captures frames and processes through repository
- Shows real-time detection overlay

**Error Handling:**
- Repository returns `Result<PlateRecognitionResult>`
- Partial results OK (e.g., detection without OCR)
- Network errors in vehicle lookup don't fail entire pipeline

## Performance Considerations

**Target Metrics:**
- Total pipeline: < 500ms per frame
- YOLO detection: < 200ms
- ML Kit OCR: < 200ms

**Optimization Tips:**
- Use ONNX Runtime threading (already enabled)
- Consider GPU delegate for TensorFlow Lite operations
- Process every N frames instead of all frames in camera mode
- Recycle bitmaps after processing

## Troubleshooting

**"Model file not found":**
- Ensure `yolo_plate_detector.onnx` is in `app/src/main/assets/`
- File should be 10-11 MB

**Low detection accuracy:**
- Check image quality and resolution
- Adjust confidence threshold in PlateDetector.kt
- Ensure plates are clearly visible

**OCR mistakes (O→0, I→1, etc.):**
- Common with ML Kit on license plates
- PlateValidator helps clean common OCR errors
- Consider adding preprocessing (contrast, denoise)

**Gradle sync fails:**
- Check internet connection for dependency downloads
- File → Invalidate Caches / Restart in Android Studio

## yad2scraper Context (WSL)

This Android app is part of a larger ecosystem with a Python-based backend in WSL.

**Location:** `/home/dan/yad2scraper/` (WSL Ubuntu)

**Purpose:** Multi-platform car marketplace scraper for Israeli sites (Yad2, AutoDeal, Eldan, etc.) with integrated license plate detection and vehicle lookup.

### Key Components in yad2scraper:

**1. License Plate Detection Pipeline:**
- Location: `yad2_scraper/license_plate_detection/`
- YOLO Detection: Uses `runs/israeli_plates/train3/weights/best.pt` (5.4 MB PyTorch)
- OCR Engine: PaddleOCR with custom-trained models
- Pipeline script: `run_full_pipeline.py`
- Detection runner: `yad2_scraper/license_plate_detection/pipeline/phase3_ocr/run_plate_detection.py`
- Crops: Saves detected plates to `yad2_scraper/license_plate_detection/crops/color/<listing_id>/`

**2. Government Vehicle Database:**
- Location: `gov_db/vehicles.db` (2 GB SQLite, ~4M vehicles)
- Source: Israeli government vehicle registry CSVs (1.1 GB total)
- Schema: 112 columns including:
  - Basic info (manufacturer, model, year, color)
  - Technical specs (engine power, fuel type, transmission)
  - Safety features (27 boolean fields: ABS, airbags, collision warning, etc.)
  - Environmental data (CO2 emissions, green index)
  - Registration info (test dates, ownership type)
- Query script: `gov_db/query_vehicle.py`
- Lookup by license plate: `mispar_rechev` field

**3. Full Pipeline (Python version):**
```python
# 1. YOLO detection on car images
model = YOLO('runs/israeli_plates/train3/weights/best.pt')
results = model(image, imgsz=640)

# 2. Crop detected plates
crop = image.crop((x1, y1, x2, y2))

# 3. PaddleOCR text recognition
ocr = PaddleOCR(use_angle_cls=True, lang='en')
text = ocr.ocr(crop_path)

# 4. Validate Israeli plate format (7-8 digits)
# 5. Query gov database by license plate number
```

**4. Model Export for Android:**
- Original model: `runs/israeli_plates/train3/weights/best.pt` (PyTorch)
- Converted model: `runs/israeli_plates/train3/weights/best.onnx` (11 MB)
- Export command: `model.export(format='onnx', imgsz=640)`
- Already copied to Android: `app/src/main/assets/yolo_plate_detector.onnx`

**5. Test Images:**
- Source: `yad2_scraper/images/` (2,657 vehicle images)
- Organized by listing ID
- Can be copied to Android for testing:
  ```bash
  cp /home/dan/yad2scraper/yad2_scraper/images/<listing_id>/*.jpg \
     /mnt/d/Projects/plate-ocr-android/app/src/main/assets/test_images/
  ```

### Differences Between Python and Android Pipelines:

| Component | Python (yad2scraper) | Android (this app) |
|-----------|---------------------|-------------------|
| Detection | YOLO via Ultralytics | YOLO via ONNX Runtime |
| OCR | PaddleOCR (custom trained) | ML Kit Text Recognition |
| Database | SQLite (local, 2GB) | REST API (planned) |
| Language | Python 3 | Kotlin |
| Platform | Ubuntu (WSL) | Android (mobile) |

**Note:** Android uses ML Kit instead of PaddleOCR because:
- Easier integration (Google Play Services)
- Smaller binary size
- Good enough accuracy for MVP
- PaddleOCR Android integration is complex

### Accessing WSL from Windows:

```bash
# Use MSYS_NO_PATHCONV=1 to prevent path conversion
MSYS_NO_PATHCONV=1 wsl.exe ls /home/dan/yad2scraper/

# Copy files from WSL to Windows
cp /home/dan/yad2scraper/file.txt /mnt/d/Projects/plate-ocr-android/
```

## Related Documentation

- **ARCHITECTURE.md** - Detailed architecture and design decisions
- **YOLO_INTEGRATION_COMPLETE.md** - YOLO model integration details
- **QUICK_START.md** - Quick start guide for YOLO testing
- **TESTING.md** - Comprehensive testing guide with static images
- **MODEL_INTEGRATION.md** - Original model integration planning
- **yad2scraper/README.md** (WSL) - Backend scraper documentation
- **yad2scraper/gov_db/README.md** (WSL) - Government database documentation
