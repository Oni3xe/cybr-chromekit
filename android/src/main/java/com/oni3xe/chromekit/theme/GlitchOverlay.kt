package com.oni3xe.chromekit.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.oni3xe.chromekit.views.GlitchOverlayView
import com.oni3xe.chromekit.views.GlitchSpec

/**
 * The glitch overlay for Compose: put it last in a full-screen Box, over the content. Null [spec]
 * draws nothing. Short bursts on a timer in the theme's red and cyan; idle (no frames) in between,
 * stopped while not visible and with reduced motion. Doesn't take touches. Use
 * `ChromekitThemeState.glitch` to follow the launcher.
 */
@Composable
fun ChromekitGlitchOverlay(spec: GlitchSpec?, modifier: Modifier = Modifier.fillMaxSize()) {
    if (spec == null) return
    val colors = CybrTheme.tokens.colors
    val primary = colors.red.toArgb()
    val secondary = colors.cyan.toArgb()
    AndroidView(factory = { GlitchOverlayView(it) }, modifier = modifier) { v ->
        v.primary = primary
        v.secondary = secondary
        v.spec = spec
    }
}
