package com.raupime.liquid

import androidx.compose.ui.window.ComposeUIViewController
import com.raupime.liquid.ui.theme.LiquidPageComposeTheme
import platform.UIKit.UIViewController

/** Entry point used by the iOS app (see `iosApp/iosApp/ContentView.swift`). */
fun MainViewController(): UIViewController = ComposeUIViewController {
    LiquidPageComposeTheme {
        LiquidOnboarding()
    }
}
