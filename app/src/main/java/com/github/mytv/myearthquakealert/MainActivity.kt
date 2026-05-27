package com.github.mytv.myearthquakealert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.mytv.myearthquakealert.ui.main.MainScreen
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyEarthQuakeAlertTheme {
                MainScreen()
            }
        }
    }
}
