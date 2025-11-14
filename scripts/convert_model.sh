#!/bin/bash
# Convert YOLO model to TensorFlow Lite for Android
# Run this script on WSL in the yad2scraper directory

set -e  # Exit on error

echo "=========================================="
echo "YOLO Model Conversion Script"
echo "=========================================="
echo ""

# Configuration
YAD2_SCRAPER_PATH="/home/dan/yad2scraper"
ANDROID_PROJECT_PATH="/mnt/d/Projects/plate-ocr-android"

# Model to convert (change this to switch models)
# Option 1: Smaller, faster model (recommended)
MODEL_PATH="$YAD2_SCRAPER_PATH/yad2_scraper/yolo_runs/lp_clean_96_rect/weights/best.pt"
OUTPUT_NAME="lp_clean_96_rect.tflite"

# Option 2: Larger, more accurate model (uncomment to use)
# MODEL_PATH="$YAD2_SCRAPER_PATH/yad2_scraper/yolo_runs/lp_qwen_reviewed_aug/weights/best.pt"
# OUTPUT_NAME="lp_qwen_reviewed_aug.tflite"

OUTPUT_PATH="$YAD2_SCRAPER_PATH/$OUTPUT_NAME"
ASSETS_PATH="$ANDROID_PROJECT_PATH/app/src/main/assets"

# Check if model exists
if [ ! -f "$MODEL_PATH" ]; then
    echo "Error: Model not found at $MODEL_PATH"
    exit 1
fi

echo "Model: $MODEL_PATH"
echo "Output: $OUTPUT_PATH"
echo ""

# Step 1: Install dependencies (if needed)
echo "[Step 1] Checking dependencies..."
pip list | grep -q ultralytics || pip install ultralytics
pip list | grep -q tf2onnx || pip install tf2onnx tensorflow

# Step 2: Export to ONNX using ultralytics CLI
echo ""
echo "[Step 2] Exporting to ONNX..."
cd "$YAD2_SCRAPER_PATH"

python -c "
from ultralytics import YOLO
model = YOLO('$MODEL_PATH')
model.export(format='onnx', imgsz=640)
print('✓ ONNX export complete')
"

# Find the generated ONNX file (usually best.onnx in the same directory)
ONNX_PATH="${MODEL_PATH%.pt}.onnx"

if [ ! -f "$ONNX_PATH" ]; then
    echo "Error: ONNX file not found at $ONNX_PATH"
    exit 1
fi

# Step 3: Convert ONNX to TFLite
echo ""
echo "[Step 3] Converting ONNX to TensorFlow Lite..."

python -m tf2onnx.convert \
    --opset 13 \
    --tflite "$OUTPUT_PATH" \
    --input "$ONNX_PATH"

if [ ! -f "$OUTPUT_PATH" ]; then
    echo "Error: TFLite conversion failed"
    exit 1
fi

# Step 4: Validate model
echo ""
echo "[Step 4] Validating TFLite model..."
python -c "
import tensorflow as tf
interpreter = tf.lite.Interpreter(model_path='$OUTPUT_PATH')
interpreter.allocate_tensors()
input_details = interpreter.get_input_details()[0]
print(f'✓ Model valid - Input shape: {input_details[\"shape\"]}')
"

# Step 5: Copy to Android assets
echo ""
echo "[Step 5] Copying to Android project..."
mkdir -p "$ASSETS_PATH"
cp "$OUTPUT_PATH" "$ASSETS_PATH/yolo_plate_detector.tflite"

# Get file size
SIZE=$(du -h "$OUTPUT_PATH" | cut -f1)

echo ""
echo "=========================================="
echo "✓ Conversion complete!"
echo "=========================================="
echo ""
echo "Model: $SIZE"
echo "Location: $ASSETS_PATH/yolo_plate_detector.tflite"
echo ""
echo "Next steps:"
echo "1. Implement PlateDetector.kt"
echo "2. Add test images to assets"
echo "3. Create test UI to process static images"
