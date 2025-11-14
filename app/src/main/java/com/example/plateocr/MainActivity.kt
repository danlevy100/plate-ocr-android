package com.example.plateocr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.plateocr.ui.screen.CameraScreen
import com.example.plateocr.ui.screen.DetectionTestScreen
import com.example.plateocr.ui.theme.PlateOCRTheme

/**
 * Main activity for the license plate OCR app.
 *
 * Shows the camera screen for capturing and processing license plates.
 * DetectionTestScreen is still available for testing with static images.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PlateOCRTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Use CameraScreen for live capture
                    CameraScreen(modifier = Modifier.padding(innerPadding))

                    // Use test images (switch back for testing):
                    // DetectionTestScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}