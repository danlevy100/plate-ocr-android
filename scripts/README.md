# Model Conversion Scripts

## Quick Start (WSL)

**Run this on your WSL machine:**

```bash
# Navigate to yad2scraper directory
cd /home/dan/yad2scraper

# Copy the conversion script from Windows to WSL
cp /mnt/d/Projects/plate-ocr-android/scripts/convert_model.sh .

# Make it executable
chmod +x convert_model.sh

# Run the conversion
./convert_model.sh
```

This will:
1. Export YOLO `.pt` → ONNX
2. Convert ONNX → TensorFlow Lite
3. Validate the model
4. Copy to Android `app/src/main/assets/yolo_plate_detector.tflite`

## Manual Conversion (Alternative)

If the script doesn't work, run these commands manually:

```bash
cd /home/dan/yad2scraper

# Install dependencies
pip install ultralytics onnx tf2onnx tensorflow

# Export to ONNX
python -c "
from ultralytics import YOLO
model = YOLO('yad2_scraper/yolo_runs/lp_clean_96_rect/weights/best.pt')
model.export(format='onnx', imgsz=640)
"

# Convert to TFLite
python -m tf2onnx.convert \
    --opset 13 \
    --tflite lp_clean_96_rect.tflite \
    --input yad2_scraper/yolo_runs/lp_clean_96_rect/weights/best.onnx

# Copy to Android
mkdir -p /mnt/d/Projects/plate-ocr-android/app/src/main/assets
cp lp_clean_96_rect.tflite /mnt/d/Projects/plate-ocr-android/app/src/main/assets/yolo_plate_detector.tflite
```

## Choosing a Model

### Option 1: lp_clean_96_rect (Recommended)
- **Size**: 5.5 MB
- **Speed**: Fast on mobile devices
- **Accuracy**: Good for most cases
- **Location**: `yad2_scraper/yolo_runs/lp_clean_96_rect/weights/best.pt`

### Option 2: lp_qwen_reviewed_aug
- **Size**: 57 MB
- **Speed**: Slower on mobile
- **Accuracy**: Higher accuracy
- **Location**: `yad2_scraper/yolo_runs/lp_qwen_reviewed_aug/weights/best.pt`

**Recommendation**: Start with Option 1. Test accuracy with your images, then upgrade to Option 2 if needed.

## Troubleshooting

**Error: "Model not found"**
- Check the path in `convert_model.sh` matches your yad2scraper structure
- Verify the model files exist: `ls yad2_scraper/yolo_runs/*/weights/best.pt`

**Error: "ultralytics not found"**
- Install: `pip install ultralytics`
- Make sure you're in the yad2scraper Python environment

**Error: "tf2onnx not found"**
- Install: `pip install tf2onnx tensorflow`

**ONNX export creates wrong file path**
- Ultralytics exports to the same directory as the `.pt` file
- Look for `best.onnx` in the weights folder

## Next Steps After Conversion

1. ✅ Model converted to `app/src/main/assets/yolo_plate_detector.tflite`
2. ⏭️ Add ML Kit dependency for OCR
3. ⏭️ Implement PlateDetector.kt
4. ⏭️ Implement OcrEngine.kt
5. ⏭️ Copy test images to assets
6. ⏭️ Create test UI
7. ⏭️ Test the pipeline!
