package com.example.plateocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.plateocr.ml.detector.PlateDetector
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for PlateDetector using real YOLO model.
 *
 * These tests run on an Android device/emulator and use the actual ONNX model.
 * Test images are from the yad2scraper dataset - known to have license plates.
 *
 * Run with: ./gradlew connectedAndroidTest
 * Or in Android Studio: Right-click → Run 'PlateDetectorTest'
 */
@RunWith(AndroidJUnit4::class)
class PlateDetectorTest {

    private lateinit var detector: PlateDetector
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        detector = PlateDetector(context)
    }

    @After
    fun teardown() {
        detector.close()
    }

    @Test
    fun testModelLoads() {
        // Just initializing the detector should load the model successfully
        detector.initialize()
        // If we got here without exception, model loaded successfully
        assertTrue("Model should initialize without errors", true)
    }

    @Test
    fun testDetectPlate1() {
        val bitmap = loadTestImage("test_plate_1.jpg")
        assertNotNull("Test image should load", bitmap)

        val result = detector.detect(bitmap!!)

        assertNotNull("Should detect a plate in test_plate_1", result)
        assertTrue("Confidence should be > 0.3", result!!.confidence > 0.3f)

        println("Test Plate 1:")
        println("  Confidence: ${result.confidence}")
        println("  Bounding box: ${result.boundingBox}")
    }

    @Test
    fun testDetectPlate2() {
        val bitmap = loadTestImage("test_plate_2.jpg")
        assertNotNull("Test image should load", bitmap)

        val result = detector.detect(bitmap!!)

        assertNotNull("Should detect a plate in test_plate_2", result)
        assertTrue("Confidence should be > 0.3", result!!.confidence > 0.3f)

        println("Test Plate 2:")
        println("  Confidence: ${result.confidence}")
        println("  Bounding box: ${result.boundingBox}")
    }

    @Test
    fun testDetectPlate3() {
        val bitmap = loadTestImage("test_plate_3.jpg")
        assertNotNull("Test image should load", bitmap)

        val result = detector.detect(bitmap!!)

        assertNotNull("Should detect a plate in test_plate_3", result)
        assertTrue("Confidence should be > 0.3", result!!.confidence > 0.3f)

        println("Test Plate 3:")
        println("  Confidence: ${result.confidence}")
        println("  Bounding box: ${result.boundingBox}")
    }

    @Test
    fun testDetectPlate4() {
        val bitmap = loadTestImage("test_plate_4.jpg")
        assertNotNull("Test image should load", bitmap)

        val result = detector.detect(bitmap!!)

        assertNotNull("Should detect a plate in test_plate_4", result)
        assertTrue("Confidence should be > 0.3", result!!.confidence > 0.3f)

        println("Test Plate 4:")
        println("  Confidence: ${result.confidence}")
        println("  Bounding box: ${result.boundingBox}")
    }

    @Test
    fun testDetectPlate5() {
        val bitmap = loadTestImage("test_plate_5.jpg")
        assertNotNull("Test image should load", bitmap)

        val result = detector.detect(bitmap!!)

        assertNotNull("Should detect a plate in test_plate_5", result)
        assertTrue("Confidence should be > 0.3", result!!.confidence > 0.3f)

        println("Test Plate 5:")
        println("  Confidence: ${result.confidence}")
        println("  Bounding box: ${result.boundingBox}")
    }

    @Test
    fun testDetectPlate6() {
        val bitmap = loadTestImage("test_plate_6.jpg")
        assertNotNull("Test image should load", bitmap)

        val result = detector.detect(bitmap!!)

        assertNotNull("Should detect a plate in test_plate_6", result)
        assertTrue("Confidence should be > 0.3", result!!.confidence > 0.3f)

        println("Test Plate 6:")
        println("  Confidence: ${result.confidence}")
        println("  Bounding box: ${result.boundingBox}")
    }

    @Test
    fun testDetectPlate7() {
        val bitmap = loadTestImage("test_plate_7.jpg")
        assertNotNull("Test image should load", bitmap)

        val result = detector.detect(bitmap!!)

        assertNotNull("Should detect a plate in test_plate_7", result)
        assertTrue("Confidence should be > 0.3", result!!.confidence > 0.3f)

        println("Test Plate 7:")
        println("  Confidence: ${result.confidence}")
        println("  Bounding box: ${result.boundingBox}")
    }

    @Test
    fun testDetectPlate8() {
        val bitmap = loadTestImage("test_plate_8.jpg")
        assertNotNull("Test image should load", bitmap)

        val result = detector.detect(bitmap!!)

        assertNotNull("Should detect a plate in test_plate_8", result)
        assertTrue("Confidence should be > 0.3", result!!.confidence > 0.3f)

        println("Test Plate 8:")
        println("  Confidence: ${result.confidence}")
        println("  Bounding box: ${result.boundingBox}")
    }

    @Test
    fun testDetectionSpeed() {
        val bitmap = loadTestImage("test_plate_1.jpg")
        assertNotNull("Test image should load", bitmap)

        // Warm up (first run loads model)
        detector.detect(bitmap!!)

        // Now measure actual inference time
        val iterations = 10
        val startTime = System.currentTimeMillis()

        repeat(iterations) {
            detector.detect(bitmap)
        }

        val endTime = System.currentTimeMillis()
        val avgTime = (endTime - startTime) / iterations.toFloat()

        println("Average detection time: ${avgTime}ms")
        assertTrue("Average detection should be < 500ms", avgTime < 500f)
    }

    @Test
    fun testBoundingBoxValid() {
        val bitmap = loadTestImage("test_plate_1.jpg")
        val result = detector.detect(bitmap!!)

        assertNotNull("Should detect a plate", result)

        val bbox = result!!.boundingBox
        assertTrue("Left should be >= 0", bbox.left >= 0)
        assertTrue("Top should be >= 0", bbox.top >= 0)
        assertTrue("Right should be <= image width", bbox.right <= bitmap.width)
        assertTrue("Bottom should be <= image height", bbox.bottom <= bitmap.height)
        assertTrue("Width should be > 0", bbox.width() > 0)
        assertTrue("Height should be > 0", bbox.height() > 0)
    }

    /**
     * Loads a test image from androidTest/assets/test_images/
     */
    private fun loadTestImage(filename: String): Bitmap? {
        return try {
            val testContext = InstrumentationRegistry.getInstrumentation().context
            val inputStream = testContext.assets.open("test_images/$filename")
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
