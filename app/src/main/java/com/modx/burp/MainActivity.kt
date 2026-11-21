package com.modx.burp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.modx.burp.ui.WorkbenchScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MODxTheme { WorkbenchScreen() } }
    }
}

@Composable
private fun MODxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFFF3028),
            secondary = Color(0xFFFF7A00),
            tertiary = Color(0xFFFFB020),
            background = Color(0xFF080808),
            surface = Color(0xFF151515),
            surfaceVariant = Color(0xFF242424)
        ),
        content = content
    )
}
