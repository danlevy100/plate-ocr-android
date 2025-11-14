# Visual Detection Test Guide

## What I Created

A visual test screen that shows YOLO detections with bounding boxes drawn on the images.

## How to Use

### Step 1: Run the App
1. In Android Studio, click **▶ Run**
2. App will launch on your device/emulator

### Step 2: Test Detections
1. You'll see a button: **"Run Detection on All Test Images"**
2. Click the button
3. Wait a few seconds while it processes
4. Scroll down to see results

### Step 3: View Results

For each test image, you'll see:
- ✅ **Green checkmark** = Plate detected
- ❌ **Red X** = No detection
- **Confidence percentage** (e.g., "87%")
- **Bounding box coordinates**
- **Image with green box** drawn around detected plate

## What the Visual Test Shows

### Success Case:
```
✅ Detected - Confidence: 87%
Box: (123, 456) to (890, 234)
[Image with green rectangle around the plate]
```

### Failure Case:
```
❌ No detection
[Original image without box]
```

## Understanding the Bounding Box

The green rectangle shows where YOLO thinks the license plate is:
- **Brighter green** = Higher confidence
- **Dimmer green** = Lower confidence
- **Percentage text** = Confidence score

## What to Look For

### Good Detection:
- ✅ Box tightly around the plate
- ✅ High confidence (>70%)
- ✅ Box not cutting off plate edges

### Problems:
- ❌ Box too big (includes non-plate area)
- ❌ Box too small (cuts off plate)
- ❌ Box in wrong location
- ❌ No box (no detection)

## Test Images

The app tests these 3 images:
1. `test_plate_1.jpg` - From listing 0lqwee8r
2. `test_plate_2.jpg` - From listing 0mal37tc
3. `test_plate_3.jpg` - From listing 1syhmeiv

All are real Israeli license plates from your yad2scraper dataset.

## Troubleshooting

### App Crashes on Button Click
**Problem**: Out of memory or model loading error

**Solution**:
- Check logcat for error
- Make sure `yolo_plate_detector.onnx` exists in assets

### "No detection" on all images
**Problem**: Model not loading or threshold too high

**Solution**:
- Check confidence threshold in `PlateDetector.kt`
- Try lowering from 0.5 to 0.3
- Check logcat for errors

### Bounding box in wrong place
**Problem**: Coordinate parsing issue

**Solution**:
- Check output parsing in `PlateDetector.kt`
- Verify model output format is [1, 5, 8400]

## Next Steps

Once you see the visual results:

1. **If detections look good** → Move to OCR integration
2. **If boxes are wrong** → Debug detection parsing
3. **If no detections** → Lower threshold or check model

## Comparing with Test 8

This visual test only shows 3 images from `res/drawable`.

To test plate 8 (the one that failed):
- Copy it to `res/drawable`
- Add to the `testImages` list in `DetectionTestScreen.kt`
- Rebuild and run

## Code Location

- Screen: `app/src/main/java/com/example/plateocr/ui/screen/DetectionTestScreen.kt`
- Main: `app/src/main/java/com/example/plateocr/MainActivity.kt`

## Expected Behavior

When you run the app:
1. Shows title and button
2. Click button → "Testing..." appears
3. After ~2-5 seconds → Results appear
4. Scroll to see all 3 test images with boxes

The visual feedback will immediately show you if YOLO is working correctly! 🎯
