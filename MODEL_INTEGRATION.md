# YOLO Model Integration Guide

## Model Converted: ✅ COMPLETE

### What We Have

**Source Model:**
- Location: `/home/dan/yad2scraper/runs/israeli_plates/train3/weights/best.pt` (WSL)
- This is the ACTUAL model used in yad2scraper project
- Referenced in: `run_full_pipeline.py`, `yolo_detection_webapp.py`, `test_yolo_performance.py`
- Size: 5.4 MB (PyTorch)

**Converted Model:**
- Format: ONNX (not TFLite - see why below)
- Location in Android project: `app/src/main/assets/yolo_plate_detector.onnx`
- Size: 10.5 MB
- Input: 640x640 RGB image
- Output: Detection results (1, 5, 8400) - [batch, (x, y, w, h, confidence), anchors]

### Why ONNX Instead of TFLite?

TFLite conversion had too many dependency issues. ONNX is actually better for this use case:

**Advantages:**
- ✅ Direct conversion from PyTorch (no intermediate steps)
- ✅ ONNX Runtime for Android is well-supported and fast
- ✅ Same model format across platforms (iOS, Web, etc.)
- ✅ Better debugging and visualization tools
- ✅ Sometimes faster than TFLite on modern devices

**Integration:**
- Use ONNX Runtime Android library instead of TensorFlow Lite
- Dependency: `implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")`

## Model Details

### Input Requirements
```
Shape: [1, 3, 640, 640]
Format: NCHW (batch, channels, height, width)
Data type: Float32
Preprocessing:
  1. Resize image to 640x640
  2. Convert BGR → RGB
  3. Normalize: pixel_value / 255.0
  4. Transpose from HWC to CHW format
```

### Output Format
```
Shape: [1, 5, 8400]
Format:
  - Dimension 0: Batch (always 1)
  - Dimension 1: [x_center, y_center, width, height, confidence]
  - Dimension 2: 8400 anchor points (detection candidates)

Post-processing needed:
  1. Filter by confidence threshold (e.g., > 0.5)
  2. Apply Non-Maximum Suppression (NMS) to remove overlapping boxes
  3. Scale coordinates back to original image size
```

### Model Performance
- Trained on Israeli license plates
- Expected accuracy: High (this is your production model)
- Inference time: ~50-150ms on modern Android devices (depends on phone)

## Integration Steps for Android

### 1. Add ONNX Runtime Dependency

In `app/build.gradle.kts`, add to dependencies:
```kotlin
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")
```

### 2. Load Model in PlateDetector.kt

```kotlin
class PlateDetector(context: Context) {
    private val ortSession: OrtSession
    private val ortEnvironment: OrtEnvironment = OrtEnvironment.getEnvironment()

    init {
        val modelBytes = context.assets.open("yolo_plate_detector.onnx").readBytes()
        ortSession = ortEnvironment.createSession(modelBytes)
    }

    fun detect(bitmap: Bitmap): DetectionResult {
        // Implementation needed
    }
}
```

### 3. Preprocessing Pipeline

```kotlin
fun preprocessImage(bitmap: Bitmap): FloatArray {
    // 1. Resize to 640x640
    val resized = Bitmap.createScaledBitmap(bitmap, 640, 640, true)

    // 2. Convert to float array in CHW format
    val input = FloatArray(1 * 3 * 640 * 640)
    val pixels = IntArray(640 * 640)
    resized.getPixels(pixels, 0, 640, 0, 0, 640, 640)

    // 3. Normalize and arrange in CHW format
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val r = ((pixel shr 16) and 0xFF) / 255.0f
        val g = ((pixel shr 8) and 0xFF) / 255.0f
        val b = (pixel and 0xFF) / 255.0f

        input[i] = r                    // Red channel
        input[640*640 + i] = g          // Green channel
        input[2*640*640 + i] = b        // Blue channel
    }

    return input
}
```

### 4. Post-processing

```kotlin
fun parseOutput(output: FloatArray): DetectionResult {
    // Output shape: [1, 5, 8400]
    // Need to:
    // 1. Find detections with confidence > threshold
    // 2. Apply NMS (Non-Maximum Suppression)
    // 3. Scale coordinates back to original image size

    val boxes = mutableListOf<BoundingBox>()

    for (i in 0 until 8400) {
        val confidence = output[4 * 8400 + i]

        if (confidence > 0.5f) {
            val x = output[0 * 8400 + i]
            val y = output[1 * 8400 + i]
            val w = output[2 * 8400 + i]
            val h = output[3 * 8400 + i]

            boxes.add(BoundingBox(x, y, w, h, confidence))
        }
    }

    // Apply NMS and return best detection
    return applyNMS(boxes)
}
```

## Testing with yad2scraper Images

Copy some test images from yad2scraper:
```bash
# In WSL
cp /home/dan/yad2scraper/yad2_scraper/images/[some_folder]/*.jpeg \
   /mnt/d/Projects/plate-ocr-android/app/src/androidTest/assets/test_images/
```

Then use them in instrumented tests to verify model accuracy.

## Next Steps

1. **Sync Gradle** in Android Studio (File → Sync Project with Gradle Files)
2. **Add ONNX Runtime dependency** to `app/build.gradle.kts`
3. **Implement PlateDetector.kt** with ONNX Runtime
4. **Test with sample images** from yad2scraper
5. **Integrate with camera** once detection works

## Resources

- ONNX Runtime Android: https://onnxruntime.ai/docs/get-started/with-java.html
- Model visualization: https://netron.app (upload yolo_plate_detector.onnx to inspect)
- Original training: `/home/dan/yad2scraper/runs/israeli_plates/train3/`

## Contact with WSL Session

If you need to re-export or modify the model, the source is in WSL at:
```
/home/dan/yad2scraper/runs/israeli_plates/train3/weights/best.pt
```

Conversion command used:
```python
from ultralytics import YOLO
model = YOLO('runs/israeli_plates/train3/weights/best.pt')
model.export(format='onnx', imgsz=640)
```
