# Test Images Setup ✅

## Test Images Added

I've copied real Israeli license plate images from your yad2scraper dataset to the Android project.

### Location 1: For Instrumented Tests
```
app/src/androidTest/assets/test_images/
├── test_plate_1.jpg (946 KB)
├── test_plate_2.jpg (295 KB)
├── test_plate_3.jpg (2.0 MB)
├── test_plate_4.jpg (2.6 MB)
└── test_plate_5.jpg (518 KB)
```

### Location 2: For Quick App Testing
```
app/src/main/res/drawable/
├── test_plate_1.jpg
├── test_plate_2.jpg
└── test_plate_3.jpg
```

These are the same images from your yad2scraper project that contain actual license plates.

## Running the Tests

### Option 1: Instrumented Tests (Recommended)

**In Android Studio:**
1. Open `app/src/androidTest/java/com/example/plateocr/PlateDetectorTest.kt`
2. Click the green ▶ button next to the class name
3. Tests will run on your device/emulator
4. Check "Run" panel for results

**From Command Line:**
```bash
./gradlew connectedAndroidTest
```

**What the tests do:**
- ✅ Load the ONNX model
- ✅ Detect plates in 5 different test images
- ✅ Verify confidence scores (> 30%)
- ✅ Validate bounding boxes
- ✅ Measure inference speed
- ✅ Print results to logcat

### Option 2: Quick Visual Test in App

**Add to MainActivity.kt or TestScreen.kt:**

```kotlin
import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.asImageBitmap
import com.example.plateocr.ml.detector.PlateDetector

@Composable
fun TestDetectionScreen() {
    var detectionResult by remember { mutableStateOf<String>("Click to test") }
    val context = LocalContext.current

    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = {
            // Load test image from drawable
            val bitmap = BitmapFactory.decodeResource(
                context.resources,
                R.drawable.test_plate_1
            )

            // Detect plate
            val detector = PlateDetector(context)
            val result = detector.detect(bitmap)
            detector.close()

            detectionResult = if (result != null) {
                """
                ✅ Plate detected!
                Confidence: ${(result.confidence * 100).toInt()}%
                Box: (${result.boundingBox.left.toInt()},
                      ${result.boundingBox.top.toInt()})
                """.trimIndent()
            } else {
                "❌ No plate detected"
            }
        }) {
            Text("Test YOLO Detection")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(detectionResult)
    }
}
```

## Expected Results

### Test Plate 1
- **Source**: yad2scraper listing 80wus8bd
- **Expected**: Should detect with confidence > 50%
- **Notes**: Clear, front-facing shot

### Test Plate 2
- **Source**: yad2scraper listing 04aphmzu
- **Expected**: Should detect with confidence > 50%
- **Notes**: Good lighting

### Test Plate 3
- **Source**: yad2scraper listing dhkwassa
- **Expected**: Should detect with confidence > 40%
- **Notes**: 2.0 MB image, high resolution

### Test Plate 4
- **Source**: yad2scraper listing 85m5vz01
- **Expected**: Should detect with confidence > 40%
- **Notes**: 2.6 MB image, very high resolution

### Test Plate 5
- **Source**: yad2scraper listing 2iowm3w6
- **Expected**: Should detect with confidence > 50%
- **Notes**: Medium size, good quality

## Viewing Test Results

**In Logcat (Android Studio):**
1. Open Logcat panel (bottom)
2. Filter by "PlateDetectorTest"
3. You'll see output like:
   ```
   Test Plate 1:
     Confidence: 0.87
     Bounding box: RectF(123.4, 456.7, 890.1, 234.5)
   ```

**Test Output:**
- Green checkmark ✅ = Test passed
- Red X ❌ = Test failed
- Each test prints confidence and bounding box

## What to Look For

### Success Indicators:
- ✅ All tests pass (green)
- ✅ Confidence > 0.3 (30%)
- ✅ Bounding boxes within image bounds
- ✅ Detection time < 500ms
- ✅ No exceptions/crashes

### If Tests Fail:

**Low Confidence (<30%)**
- Image might not have visible plate
- Model threshold might be too high
- Try lowering `CONFIDENCE_THRESHOLD` in PlateDetector.kt

**No Detection**
- Check logcat for errors
- Verify model file exists: `app/src/main/assets/yolo_plate_detector.onnx`
- Make sure ONNX Runtime dependency loaded

**Slow Performance (>500ms)**
- Normal on first run (model loading)
- Should be ~100-150ms on subsequent runs
- Emulator will be slower than physical device

## Next Steps After Tests Pass

1. ✅ **Integrate with Camera**
   - Use CameraX to capture live images
   - Pass to PlateDetector
   - Show bounding box overlay

2. ✅ **Add OCR**
   - Crop detected region
   - Pass to OcrEngine
   - Extract plate number

3. ✅ **Test Full Pipeline**
   - Camera → YOLO → OCR → Validation → API
   - End-to-end test with real plates

## Source Images Info

These images come from your yad2scraper project:
- Real Israeli vehicle listings
- Contains actual license plates
- Already scraped and processed
- Known to work with your YOLO model

## Adding More Test Images

To add more test images:

```bash
# In WSL
cp /home/dan/yad2scraper/yad2_scraper/images/[listing_id]/[image.jpeg] \
   /mnt/d/Projects/plate-ocr-android/app/src/androidTest/assets/test_images/test_plate_X.jpg
```

Then add a new test in `PlateDetectorTest.kt`.

## Troubleshooting

**Can't find test images:**
```
FileNotFoundException: test_images/test_plate_1.jpg
```
Solution: Make sure you synced Gradle after adding the files. Clean and rebuild.

**Model not found:**
```
IllegalStateException: Failed to load YOLO model
```
Solution: Check `app/src/main/assets/yolo_plate_detector.onnx` exists (should be 11 MB).

**Tests not running:**
Solution: Make sure you have a device/emulator running. Instrumented tests require an Android device.

## Success! 🎉

Once tests pass, you know:
- ✅ YOLO model loads correctly
- ✅ ONNX Runtime works on Android
- ✅ Detection pipeline is functional
- ✅ Ready for camera integration
- ✅ Can move to OCR implementation
