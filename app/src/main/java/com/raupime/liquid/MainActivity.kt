package com.raupime.liquid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.raupime.liquid.ui.theme.LiquidPageComposeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiquidPageComposeTheme {
                LiquidOnboarding()
            }

        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LiquidOnboardingPreview() {
    LiquidPageComposeTheme {
        LiquidOnboarding()
    }
}
