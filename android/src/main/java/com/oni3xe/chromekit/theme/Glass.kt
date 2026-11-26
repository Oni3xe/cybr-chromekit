package com.oni3xe.chromekit.theme

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.oni3xe.chromekit.glassGlow
import com.oni3xe.chromekit.views.GlassBackdropTint
import com.oni3xe.chromekit.views.GlassBlur

/**
 * For the app's edge-to-edge background layer (wallpaper, photo), drawn *behind* a chamfered
 * [com.oni3xe.chromekit.hudScreenFrame] so it also shows in the status and navigation bars.
 * With glass on: API 31+ blurs it by the theme's glass blur radius (RenderEffect); with the
 * "glass" blur style ([CybrTokens.glassBlur]) API 33+ adds bloom, a saturation/tone boost and
 * fine grain (one AGSL RuntimeShader chained after the blur), API 31-32 the saturation/tone
 * boost plus a grain overlay. Below API 31 there is no blur: the glass style still gets the
 * grain overlay. This modifier does not draw the theme-`bg` tint — that belongs *inside* the
 * frame ([chromekitGlassTint] / [com.oni3xe.chromekit.HudWindow]). Glass off: no-op.
 */
fun Modifier.chromekitGlassBackground(tokens: CybrTokens): Modifier {
    val g = tokens.glass ?: return this
    val spec = tokens.glassBlur
    val blurred = Build.VERSION.SDK_INT >= 31 && g.blurRadiusDp > 0f
    var m = this
    if (blurred) {
        m = m.graphicsLayer {
            renderEffect = GlassBlur.blurEffect(g.blurRadiusDp.dp.toPx(), spec)?.asComposeRenderEffect()
            clip = true
        }
    }
    if (spec != null && !GlassBlur.effectHasGrain) {
        val brush = ShaderBrush(GlassBlur.grainShader())
        val alpha = GlassBlur.grainPaintAlpha(spec) / 255f
        m = m.drawWithContent { drawContent(); drawRect(brush, alpha = alpha) }
    }
    return m
}

/**
 * Theme-`bg` overlay for the *inside* of a chamfered [com.oni3xe.chromekit.hudScreenFrame].
 * Alpha is [GlassBackdropTint.ALPHA_BLURRED] (0.65) when a blur is in effect, else
 * [GlassBackdropTint.ALPHA_FALLBACK] (0.70). No-op with glass off. [HudWindow] applies this
 * inside the frame automatically; do not put it on the edge-to-edge backdrop.
 */
fun Modifier.chromekitGlassTint(tokens: CybrTokens): Modifier {
    val g = tokens.glass ?: return this
    val blurred = Build.VERSION.SDK_INT >= 31 && g.blurRadiusDp > 0f
    val tint = tokens.colors.bg.copy(alpha = GlassBackdropTint.alpha(blurred))
    return drawWithContent {
        drawContent()
        drawRect(tint)
    }
}

/** The glass glow for a key element (clock, active button); no-op with glass off. */
fun Modifier.chromekitGlassGlow(tokens: CybrTokens): Modifier {
    val g = tokens.glass ?: return this
    return glassGlow(tokens.colors.red, g.glowAlpha, g.glowRadiusDp.dp)
}
