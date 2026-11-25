package com.oni3xe.chromekit.views

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi

/*
 * chromekit glass blur: framework-only (no Compose), so Views apps (cybr-launcher compiles this
 * folder in via its chromekit submodule) and the Compose kit share one implementation. Numbers come
 * from tokens/themes.json "blurStyle" (GeneratedThemes.BLUR_GLASS_*).
 */

/** How glass panels' backdrop blur looks. Unknown or missing values read as [DEFAULT] ([GLASS]). */
enum class BlurStyle(val value: String) {
    /** The blur alone, no grain. */
    PLAIN("plain"),
    /** Blur + saturation/tone boost + bloom on bright areas + an edge light band + fine grain. */
    GLASS("glass");

    companion object {
        val DEFAULT = GLASS

        /** Exact, case-sensitive match on [value]; anything else (or null) is [DEFAULT]. */
        fun parse(value: String?): BlurStyle = entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}

/** The glass style's numbers (themes.json "blurStyle.glass"). */
data class GlassBlurSpec(
    val saturation: Float,
    val tone: Float,
    val bloomThreshold: Float,
    val bloomStrength: Float,
    val edgeAlpha: Float,
    val edgeWidthDp: Float,
    val grainAlpha: Float,
)

/** Pure math behind [GlassBlur], unit tested on the JVM. */
object GlassBlurMath {
    private const val LR = 0.2126f
    private const val LG = 0.7152f
    private const val LB = 0.0722f

    /** A 4x5 colour matrix: saturation around Rec. 709 luma, then the tone gain. Alpha untouched. */
    fun colorMatrix(saturation: Float, tone: Float): FloatArray {
        val s = saturation
        val i = 1f - s
        val r = floatArrayOf(LR * i + s, LG * i, LB * i, 0f, 0f)
        val g = floatArrayOf(LR * i, LG * i + s, LB * i, 0f, 0f)
        val b = floatArrayOf(LR * i, LG * i, LB * i + s, 0f, 0f)
        for (row in listOf(r, g, b)) for (k in 0 until 3) row[k] *= tone
        return r + g + b + floatArrayOf(0f, 0f, 0f, 1f, 0f)
    }

    /** Deterministic grain in [-1, 1] for pixel ([x], [y]): the same tile every run. */
    fun grain(x: Int, y: Int): Float {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return ((h and 0xFFFF) / 65535f) * 2f - 1f
    }
}

object GlassBlur {
    /** Grain tile edge, px. Fine grain: one pixel per grain. */
    const val GRAIN_TILE_PX = 64

    /** Bloom sample radius, px (the backdrop is already blurred, so a few taps read as a soft glow). */
    private const val BLOOM_RADIUS_PX = 6f

    /**
     * AGSL for API 33+: on the (already blurred) [content], a soft bloom from 8 taps of the bright
     * pass, then saturation and tone, then fine grain. Premultiplied in, premultiplied out.
     */
    const val SHADER: String = """
uniform shader content;
uniform float sat;
uniform float tone;
uniform float thr;
uniform float bloom;
uniform float grain;
uniform float radius;
half4 main(float2 p) {
    half4 c = content.eval(p);
    float a = max(float(c.a), 0.0001);
    float3 rgb = float3(c.rgb) / a;
    float3 b = float3(0.0);
    for (int i = 0; i < 8; i++) {
        float ang = float(i) * 0.7853982;
        half4 s = content.eval(p + float2(cos(ang), sin(ang)) * radius);
        float3 sr = float3(s.rgb) / max(float(s.a), 0.0001);
        float l = dot(sr, float3(0.2126, 0.7152, 0.0722));
        b += sr * max(l - thr, 0.0) / max(1.0 - thr, 0.0001);
    }
    rgb += b * (bloom / 8.0);
    float lum = dot(rgb, float3(0.2126, 0.7152, 0.0722));
    rgb = mix(float3(lum), rgb, sat) * tone;
    float n = fract(sin(dot(floor(p), float2(12.9898, 78.233))) * 43758.5453) - 0.5;
    rgb += n * grain * 2.0;
    return half4(half3(clamp(rgb, 0.0, 1.0) * a), half(a));
}
"""

    /**
     * The effect for an already-blurred backdrop: API 33+ the [SHADER] (bloom, saturation/tone,
     * grain); API 31-32 the colour matrix only (draw [grainShader] over it); below 31 null (use
     * [colorFilter] on the paint plus [grainShader]).
     */
    fun backdropEffect(spec: GlassBlurSpec): RenderEffect? = when {
        Build.VERSION.SDK_INT >= 33 -> shaderEffect(spec)
        Build.VERSION.SDK_INT >= 31 -> RenderEffect.createColorFilterEffect(colorFilter(spec))
        else -> null
    }

    /** True when [backdropEffect] already includes the grain (API 33+). */
    val effectHasGrain: Boolean get() = Build.VERSION.SDK_INT >= 33

    /**
     * Blur of [radiusPx] plus, with [spec], the glass look chained on top (API 31+; null below).
     * For a live background layer (Compose graphicsLayer, View.setRenderEffect).
     */
    fun blurEffect(radiusPx: Float, spec: GlassBlurSpec?): RenderEffect? {
        if (Build.VERSION.SDK_INT < 31 || radiusPx <= 0f) return null
        val blur = RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
        val glass = spec?.let { backdropEffect(it) } ?: return blur
        return RenderEffect.createChainEffect(glass, blur)
    }

    @RequiresApi(33)
    private fun shaderEffect(spec: GlassBlurSpec): RenderEffect {
        val s = RuntimeShader(SHADER)
        s.setFloatUniform("sat", spec.saturation)
        s.setFloatUniform("tone", spec.tone)
        s.setFloatUniform("thr", spec.bloomThreshold)
        s.setFloatUniform("bloom", spec.bloomStrength)
        s.setFloatUniform("grain", spec.grainAlpha)
        s.setFloatUniform("radius", BLOOM_RADIUS_PX)
        return RenderEffect.createRuntimeShaderEffect(s, "content")
    }

    /** Saturation + tone as a paint colour filter (every API level). */
    fun colorFilter(spec: GlassBlurSpec) = ColorMatrixColorFilter(GlassBlurMath.colorMatrix(spec.saturation, spec.tone))

    @Volatile private var tile: Bitmap? = null

    /** The fine grain tile: light and dark pixels with alpha by [GlassBlurMath.grain]. */
    fun grainTile(): Bitmap = tile ?: synchronized(this) {
        tile ?: Bitmap.createBitmap(GRAIN_TILE_PX, GRAIN_TILE_PX, Bitmap.Config.ARGB_8888).also { bmp ->
            val px = IntArray(GRAIN_TILE_PX * GRAIN_TILE_PX)
            for (y in 0 until GRAIN_TILE_PX) for (x in 0 until GRAIN_TILE_PX) {
                val n = GlassBlurMath.grain(x, y)
                val a = (kotlin.math.abs(n) * 255).toInt().coerceIn(0, 255)
                px[y * GRAIN_TILE_PX + x] = if (n >= 0f) Color.argb(a, 255, 255, 255) else Color.argb(a, 0, 0, 0)
            }
            bmp.setPixels(px, 0, GRAIN_TILE_PX, 0, 0, GRAIN_TILE_PX, GRAIN_TILE_PX)
            tile = bmp
        }
    }

    /** Repeating grain; draw it with paint alpha [grainPaintAlpha]. */
    fun grainShader(): Shader = BitmapShader(grainTile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

    /** Paint alpha (0-255) for the grain tile at the spec's strength. */
    fun grainPaintAlpha(spec: GlassBlurSpec): Int = (spec.grainAlpha * 2f * 255f).toInt().coerceIn(0, 255)

    /**
     * The edge light band's gradient for a panel spanning [l],[t],[r],[b]: [color] at the spec's
     * edge alpha at the top-start corner (top-end in RTL), fading to 0 at the opposite corner. Stroke
     * it along the panel's own cut outline, inset by half the band width.
     */
    fun edgeGradient(l: Float, t: Float, r: Float, b: Float, rtl: Boolean, color: Int, spec: GlassBlurSpec): Shader {
        val a = (spec.edgeAlpha * 255f).toInt().coerceIn(0, 255)
        val start = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
        val end = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
        val (x0, x1) = if (rtl) r to l else l to r
        return LinearGradient(x0, t, x1, b, start, end, Shader.TileMode.CLAMP)
    }
}
