package com.piash.priya

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.piash.priya.ui.PriyaApp
import com.piash.priya.ui.theme.PriyaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PriyaTheme {
                PriyaApp()
            }
        }
    }
}
