# License Plate Recognition Pipeline Optimizations

## Current Pipeline Performance

✅ **YOLO Detection**: 60-85% confidence
✅ **ML Kit OCR**: Successfully reading Israeli plate numbers
✅ **End-to-end**: Working on test images

## Optimizations Applied

### 1. **Image Preprocessing for OCR**

**Location**: `OcrEngine.preprocessForOcr()`

**Changes**:
- Normalize cropped plate to **200px height** (maintains aspect ratio)
- Consistent sizing improves ML Kit accuracy across different image qualities
- Prepares for camera images which vary in resolution

**Why**:
- Camera images will have different resolutions (720p, 1080p, 4K)
- ML Kit works best with consistent input sizes
- Small plates (far away) vs large plates (close up) now normalized

### 2. **Enhanced Text Cleaning - Digits Only**

**Location**: `OcrEngine.cleanPlateText()`

**Changes**:
- Added more letter→digit normalizations (Q→0, L→1, Z→2, G→6, etc.)
- **Strict digit filtering**: `.filter { it.isDigit() }` removes all letters
- Better prefix removal (IL, ISR, ISRAEL)

**Why**:
- Israeli plates are 7-8 digits (some new ones have 1-2 letters)
- Focusing on digits reduces false positives
- Easy to relax if newer plate formats need letters

### 3. **Improved Validation**

**Location**: `OcrEngine.isValidIsraeliPlate()`

**Current Logic**:
- Must be 6-8 characters
- Must have at least 5 digits
- Flexible for edge cases

**Recommendation**: Once you test with camera, tighten to 7-8 digits exactly

## Camera-Specific Optimizations (Next Steps)

### 1. **Image Resolution Strategy**

For camera preview:
```kotlin
// Recommended camera resolution
targetResolution = Size(1280, 720)  // 720p

// Why not higher?
// - 1080p/4K = slower processing
// - 720p = good balance of quality vs speed
// - YOLO trained on 640x640, doesn't need 4K input
```

### 2. **Real-time Performance**

**Current**: Batch processing (loads full images)
**Camera**: Stream processing

Optimizations needed:
- Use CameraX ImageAnalysis (not full bitmap)
- Only process every Nth frame (skip frames for speed)
- Run detection in background thread (already using coroutines)

### 3. **Auto-focus & Lighting**

Camera configuration:
```kotlin
camera.cameraControl.setZoomRatio(1.2f)  // Slight zoom for plates
camera.cameraControl.enableTorch(true)   // Flash for night
```

### 4. **Image Quality Filters**

Before processing camera frames:
```kotlin
// Skip blurry frames (camera shake)
fun isBlurry(bitmap: Bitmap): Boolean {
    // Laplacian variance check
    // Skip if variance < threshold
}

// Skip dark frames (poor lighting)
fun isToDark(bitmap: Bitmap): Boolean {
    // Average brightness check
}
```

## Performance Targets

### Current (Test Images):
- Detection: ~2-3 seconds per image
- OCR: ~500ms per plate
- **Total**: ~3 seconds

### Camera Target:
- Detection: <200ms per frame
- OCR: <300ms per detection
- **Total**: <500ms (2 FPS acceptable for scanning)

## Memory Optimizations

### Already Applied:
✅ Downsample display images to 1080px (prevents OOM)
✅ Close detectors/engines when done
✅ Process in IO dispatcher

### For Camera:
- Reuse bitmap buffers (avoid GC)
- Limit queue size (max 2 pending frames)
- Release camera preview when not in use

## Testing Recommendations

### Before Camera Integration:

1. **Test more real images**:
   - Different lighting conditions
   - Different angles (not just front-facing)
   - Dirty/damaged plates
   - Different plate formats (old vs new)

2. **Measure accuracy**:
   - Create ground truth dataset (20-30 images)
   - Track detection rate, OCR accuracy
   - Identify failure patterns

3. **Edge cases**:
   - Very small plates (far away)
   - Very close plates (cropped)
   - Angled plates (perspective distortion)

## Next Steps Priority

1. ✅ **Done**: Image preprocessing, digit-only cleaning
2. 🎯 **Next**: Camera integration with CameraX
3. 📊 **Then**: Real-world testing and accuracy measurement
4. 🔧 **Finally**: Fine-tune thresholds based on camera data

## Code Changes Summary

**Modified Files**:
- `app/src/main/java/com/example/plateocr/ml/ocr/OcrEngine.kt`
  - Added `preprocessForOcr()` - normalizes image size
  - Enhanced `cleanPlateText()` - strict digit filtering
  - More letter→digit normalizations

**Benefits**:
- Better OCR consistency across image qualities
- Prepared for variable camera resolutions
- Fewer false positives from letter misreads

**Trade-offs**:
- Slightly slower OCR (~50ms added for resize)
- May need to relax digit-only filter for new plate formats

## Configuration Tuning

If you need to adjust for different scenarios:

```kotlin
// In OcrEngine.kt

// OCR image size (current: 200px height)
val targetHeight = 200  // Increase for higher quality, decrease for speed

// YOLO confidence (current: 0.5)
const val CONFIDENCE_THRESHOLD = 0.5f  // Lower to catch more plates, higher for precision

// Plate validation (current: 6-8 chars, 5+ digits)
if (cleaned.length !in 7..8)  // Tighten after camera testing
if (digitCount < 7)  // Require more digits for stricter validation
```

## Expected Camera Pipeline

```
1. Camera Preview (720p, 30 FPS)
   ↓
2. Frame Sampler (process every 3rd frame = 10 FPS)
   ↓
3. YOLO Detection (~150ms)
   ↓ (if plate detected)
4. Crop Plate Region
   ↓
5. Preprocess (resize to 200px height)
   ↓
6. ML Kit OCR (~300ms)
   ↓
7. Clean & Validate (digits only)
   ↓
8. Display Result / Query Database
```

**Total latency**: ~500ms from camera to result
**User experience**: Point camera → See result in 0.5s
