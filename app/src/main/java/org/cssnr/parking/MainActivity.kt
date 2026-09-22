package org.cssnr.parking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.cssnr.parking.ui.ParKingApp
import org.cssnr.parking.ui.theme.ParKingTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ParKingTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ParKingApp()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ParKingAppPreview() {
    ParKingTheme {
        ParKingApp()
    }
}