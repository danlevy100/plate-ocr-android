#!/usr/bin/env python3
"""
Convert YOLO .pt model to TensorFlow Lite format for Android.

This script:
1. Exports YOLO .pt → ONNX format
2. Converts ONNX → TensorFlow Lite
3. Validates the conversion
4. Copies to Android assets folder (if path provided)

Usage:
    python convert_yolo_model.py --weights path/to/best.pt --output model.tflite

Requirements:
    pip install ultralytics onnx tf2onnx tensorflow
"""

import argparse
import os
import subprocess
import sys
from pathlib import Path


def export_to_onnx(weights_path: str, output_dir: str) -> str:
    """Export YOLO .pt model to ONNX format."""
    print(f"\n[1/3] Exporting {weights_path} to ONNX...")

    try:
        from ultralytics import YOLO

        model = YOLO(weights_path)

        # Export to ONNX format
        # imgsz=640 is the input size for YOLO
        onnx_path = model.export(format='onnx', imgsz=640)

        print(f"✓ ONNX export complete: {onnx_path}")
        return onnx_path

    except Exception as e:
        print(f"✗ ONNX export failed: {e}")
        sys.exit(1)


def convert_to_tflite(onnx_path: str, tflite_path: str) -> bool:
    """Convert ONNX model to TensorFlow Lite."""
    print(f"\n[2/3] Converting ONNX to TensorFlow Lite...")

    try:
        # Use tf2onnx command line tool
        cmd = [
            "python", "-m", "tf2onnx.convert",
            "--opset", "13",
            "--tflite", tflite_path,
            "--input", onnx_path
        ]

        result = subprocess.run(cmd, capture_output=True, text=True)

        if result.returncode != 0:
            print(f"✗ TFLite conversion failed:")
            print(result.stderr)
            return False

        if os.path.exists(tflite_path):
            size_mb = os.path.getsize(tflite_path) / (1024 * 1024)
            print(f"✓ TFLite conversion complete: {tflite_path} ({size_mb:.2f} MB)")
            return True
        else:
            print(f"✗ TFLite file not created")
            return False

    except Exception as e:
        print(f"✗ TFLite conversion failed: {e}")
        return False


def validate_tflite(tflite_path: str) -> bool:
    """Validate the TFLite model can be loaded."""
    print(f"\n[3/3] Validating TFLite model...")

    try:
        import tensorflow as tf

        # Load and validate
        interpreter = tf.lite.Interpreter(model_path=tflite_path)
        interpreter.allocate_tensors()

        # Get input/output details
        input_details = interpreter.get_input_details()
        output_details = interpreter.get_output_details()

        print(f"✓ Model validated successfully!")
        print(f"\nModel Info:")
        print(f"  Input shape: {input_details[0]['shape']}")
        print(f"  Input type: {input_details[0]['dtype']}")
        print(f"  Number of outputs: {len(output_details)}")

        return True

    except Exception as e:
        print(f"✗ Model validation failed: {e}")
        return False


def copy_to_android_assets(tflite_path: str, android_project_path: str) -> bool:
    """Copy TFLite model to Android assets folder."""
    try:
        assets_dir = Path(android_project_path) / "app" / "src" / "main" / "assets"
        assets_dir.mkdir(parents=True, exist_ok=True)

        target_path = assets_dir / "yolo_plate_detector.tflite"

        import shutil
        shutil.copy(tflite_path, target_path)

        print(f"\n✓ Copied to Android assets: {target_path}")
        return True

    except Exception as e:
        print(f"✗ Failed to copy to Android assets: {e}")
        return False


def main():
    parser = argparse.ArgumentParser(description='Convert YOLO model to TensorFlow Lite')
    parser.add_argument('--weights', required=True, help='Path to YOLO .pt weights file')
    parser.add_argument('--output', required=True, help='Output .tflite file path')
    parser.add_argument('--android-project', help='Path to Android project (optional, for auto-copy to assets)')

    args = parser.parse_args()

    # Validate input file exists
    if not os.path.exists(args.weights):
        print(f"Error: Weights file not found: {args.weights}")
        sys.exit(1)

    print("=" * 60)
    print("YOLO → TensorFlow Lite Conversion")
    print("=" * 60)

    # Step 1: Export to ONNX
    onnx_path = export_to_onnx(args.weights, os.path.dirname(args.output))

    # Step 2: Convert to TFLite
    if not convert_to_tflite(onnx_path, args.output):
        sys.exit(1)

    # Step 3: Validate
    if not validate_tflite(args.output):
        sys.exit(1)

    # Optional: Copy to Android project
    if args.android_project:
        copy_to_android_assets(args.output, args.android_project)

    print("\n" + "=" * 60)
    print("✓ Conversion complete!")
    print("=" * 60)
    print(f"\nNext steps:")
    print(f"1. Place {args.output} in: app/src/main/assets/yolo_plate_detector.tflite")
    print(f"2. Implement PlateDetector in Android")
    print(f"3. Test with sample images")


if __name__ == "__main__":
    main()
