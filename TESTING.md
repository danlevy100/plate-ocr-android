# Testing with Static Images

This guide explains how to test the license plate OCR pipeline with static images before implementing camera functionality.

## Setup Test Images

### Step 1: Create Assets Folder

Create the test images directory:
```bash
mkdir -p app/src/main/assets/test_images
```

### Step 2: Copy Images from yad2scraper

On your WSL machine, copy test images to the Android project:

```bash
# Find some good test images in yad2scraper
cd /home/dan/yad2scraper

# Copy a few test images (adjust paths as needed)
cp path/to/test/images/*.jpg /mnt/d/Projects/plate-ocr-android/app/src/main/assets/test_images/

# Or manually select specific images:
cp image1.jpg /mnt/d/Projects/plate-ocr-android/app/src/main/assets/test_images/test1.jpg
cp image2.jpg /mnt/d/Projects/plate-ocr-android/app/src/main/assets/test_images/test2.jpg
```

**Recommended test set:**
- At least 5-10 images
- Mix of clear and challenging plates
- Different lighting conditions
- Various angles
- Include some known ground truth labels

### Step 3: Create Ground Truth File (Optional)

Create `app/src/main/assets/test_images/ground_truth.txt` with expected results:

```
test1.jpg,1234567
test2.jpg,12345678
test3.jpg,2345678
...
```

This helps measure accuracy automatically.

## Running Tests

### Step 1: Convert YOLO Model

Before testing, convert the YOLO model (see `scripts/README.md`):

```bash
# On WSL
cd /home/dan/yad2scraper
./convert_model.sh
```

This creates `app/src/main/assets/yolo_plate_detector.tflite`.

### Step 2: Sync Gradle

In Android Studio:
1. File → Sync Project with Gradle Files
2. Wait for dependencies to download
3. Resolve any errors

### Step 3: Run the App

1. Connect a device or start an emulator
2. Click Run (green play button) or Ctrl+R
3. The app will show the Test Screen

### Step 4: Test Images

The Test Screen shows:
- List of images in `assets/test_images/`
- Tap an image to process it
- View results:
  - Detection confidence
  - Bounding box size
  - Raw OCR text
  - Cleaned plate number
  - Validation status
  - Processing time

## Understanding Results

### Detection Results

**Good detection:**
- Confidence > 50%
- Bounding box covers 2-10% of image
- Box tightly fits plate

**Poor detection:**
- Confidence < 30%
- Box too large or too small
- Multiple overlapping detections

### OCR Results

**Good OCR:**
- Raw text close to expected digits
- Cleaned text is 7-8 digits
- Validation passes
- High confidence (> 70%)

**Poor OCR:**
- Letters mixed with digits (O, I, l, etc.)
- Missing or extra digits
- Validation fails
- Low confidence (< 50%)

### Israeli Plate Validation

Valid formats:
- 7 digits: `123-45-678` (old)
- 8 digits: `123-45-6789` (new)

Common failures:
- OCR mistakes: `0` → `O`, `1` → `I`, `8` → `B`
- Missing digits due to poor image quality
- Extra characters from background

## Measuring Accuracy

If you have ground truth labels, calculate:

**Detection Rate:**
```
Plates detected / Total images
```

**OCR Accuracy:**
```
Correct readings / Detected plates
```

**End-to-End Accuracy:**
```
Correct readings / Total images
```

Target benchmarks:
- Detection: > 95%
- OCR: > 80% (depends on image quality)
- End-to-end: > 75%

## Troubleshooting

### "Model file not found"

**Problem:** TFLite model missing
**Solution:** Run model conversion script (see `scripts/README.md`)

### "No test images found"

**Problem:** No images in `assets/test_images/`
**Solution:** Copy images from yad2scraper or add your own

### Low detection confidence

**Causes:**
- Images too small (< 640px)
- Plate not visible or occluded
- Model mismatch (wrong model for image type)

**Solutions:**
- Use higher resolution images
- Ensure plates are clearly visible
- Try the larger model (lp_qwen_reviewed_aug)

### Low OCR accuracy

**Causes:**
- Poor bounding box (cuts off digits)
- Low resolution crop
- Image blur or noise

**Solutions:**
- Adjust bounding box padding in Repository
- Add preprocessing (grayscale, contrast, denoise)
- Consider PaddleOCR instead of ML Kit

### App crashes on model load

**Problem:** Out of memory or corrupt model
**Solution:**
1. Check model file size is correct (5-50MB)
2. Try the smaller model first
3. Ensure model conversion completed successfully

## Next Steps After Testing

Once static image testing shows good results:

1. ✅ **Detection working** → Proceed to CameraX integration
2. ✅ **OCR working** → Proceed to real-time processing
3. ❌ **Detection failing** → Tune model parameters or retrain
4. ❌ **OCR failing** → Add preprocessing or switch to PaddleOCR

## Comparing with yad2scraper

If you have accuracy metrics from yad2scraper testing:

1. Process the same test images in Android
2. Compare:
   - Detection rates
   - OCR accuracy
   - Processing times

Expected differences:
- **Android may be slower** (mobile hardware vs desktop)
- **OCR may be less accurate** (ML Kit vs PaddleOCR)
- **Detection should be similar** (same YOLO model)

If Android results are significantly worse:
- Check model conversion (may have introduced errors)
- Verify input preprocessing matches Python version
- Consider porting PaddleOCR instead of ML Kit

## Performance Targets

For a usable mobile app:
- **Processing time:** < 500ms per frame
- **Detection latency:** < 200ms
- **OCR latency:** < 200ms
- **End-to-end:** < 500ms

If too slow:
- Use smaller YOLO model
- Reduce image resolution
- Enable GPU delegate
- Process every N frames instead of all frames
