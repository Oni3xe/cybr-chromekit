package com.oni3xe.chromekit.theme

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import com.oni3xe.chromekit.glassGlow

/**
 * For the app's background layer (wallpaper, photo) behind glass panels: blurs it by the active
 * theme's glass blur radius on API 31+ (RenderEffect via Modifier.blur, no extra dependency). Below
 * API 31, or with glass off, it does nothing and panels stay translucent without blur.
 */
fun Modifier.chromekitGlassBackground(tokens: CybrTokens): Modifier {
    val g = tokens.glass ?: return this
    if (Build.VERSION.SDK_INT < 31 || g.blurRadiusDp <= 0f) return this
    return blur(g.blurRadiusDp.dp, BlurredEdgeTreatment.Rectangle)
}

/** The glass glow for a key element (clock, active button); no-op with glass off. */
fun Modifier.chromekitGlassGlow(tokens: CybrTokens): Modifier {
    val g = tokens.glass ?: return this
    return glassGlow(tokens.colors.red, g.glowAlpha, g.glowRadiusDp.dp)
}
