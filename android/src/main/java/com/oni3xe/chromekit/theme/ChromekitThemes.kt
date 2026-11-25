package com.oni3xe.chromekit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** One theme's resolved colours as ARGB longs; generated from tokens/themes.json into Themes.kt. */
internal data class ThemePalette(
    val id: String, val label: String, val colorName: String, val lowContrast: Boolean,
    val bg: Long, val surface1: Long, val surface2: Long, val scrim: Long,
    val redBright: Long, val red: Long, val redDim: Long, val redDeep: Long,
    val cyanBright: Long, val cyan: Long, val cyanDim: Long, val cyanDeep: Long,
    val text: Long, val textMuted: Long, val disabled: Long,
    val green: Long, val gold: Long, val error: Long, val warning: Long,
    val glass: GlassStyle, val shadow: ShadowStyle,
) {
    fun toColors() = CybrColors(
        bg = Color(bg), surface1 = Color(surface1), surface2 = Color(surface2), scrim = Color(scrim),
        redBright = Color(redBright), red = Color(red), redDim = Color(redDim), redDeep = Color(redDeep),
        cyanBright = Color(cyanBright), cyan = Color(cyan), cyanDim = Color(cyanDim), cyanDeep = Color(cyanDeep),
        text = Color(text), textMuted = Color(textMuted), disabled = Color(disabled),
        green = Color(green), gold = Color(gold), error = Color(error), warning = Color(warning),
    )
}

/**
 * The glass variant's values (tokens/themes.json "glass", per-theme overrides applied).
 * [surfaceAlpha]: opacity of surface.1, surface.2 and scrim. [borderAlpha]: the light hairline
 * (theme text colour at this alpha). [blurRadiusDp]: background blur, API 31+ only.
 * [glowAlpha] / [glowRadiusDp]: the soft primary-accent glow on panels and key elements.
 */
@Immutable
data class GlassStyle(
    val surfaceAlpha: Float,
    val borderAlpha: Float,
    val blurRadiusDp: Float,
    val glowAlpha: Float,
    val glowRadiusDp: Float,
)

/**
 * Shadow (tokens/themes.json "shadow"): a subtle neon glow radiating outward from each panel's edge
 * in the theme's primary colour, [alpha] fading out to [radiusDp]. Independent of glass.
 */
@Immutable
data class ShadowStyle(val alpha: Float, val radiusDp: Float)

/**
 * A selectable chromekit theme. [tokens] is the solid token set (type, spacing, shapes unchanged);
 * [glassTokens] is the same theme with the glass variant layered on.
 */
class ChromekitThemeInfo internal constructor(
    val id: String,
    /** Code name shown in the picker, e.g. KATANA. Ids never change; names may. */
    val label: String,
    /** Colour family, e.g. Red. */
    val colorName: String,
    /** Softer contrast by design (Mocha). */
    val lowContrast: Boolean,
    val tokens: CybrTokens,
    val glass: GlassStyle,
    val shadow: ShadowStyle,
) {
    /** "KATANA · Red". */
    val displayName: String get() = "$label · $colorName"

    val glassTokens: CybrTokens by lazy {
        val c = tokens.colors
        val a = glass.surfaceAlpha
        tokens.copy(
            colors = c.copy(surface1 = c.surface1.copy(alpha = a), surface2 = c.surface2.copy(alpha = a), scrim = c.scrim.copy(alpha = a)),
            glass = glass,
        )
    }

    fun tokens(glass: Boolean): CybrTokens = if (glass) glassTokens else tokens

    /** The token set with glass and/or Shadow layered on. */
    fun tokens(glass: Boolean, shadow: Boolean): CybrTokens {
        val base = tokens(glass)
        return if (shadow) base.copy(shadow = this.shadow) else base
    }
}

/**
 * The theme registry: all themes from tokens/themes.json, dark only, in picker order. Red is the
 * default and identical to [ChromekitTokens]. Unknown or missing ids resolve to the default.
 */
object ChromekitThemes {
    const val DEFAULT_ID: String = GeneratedThemes.DEFAULT_ID

    /** Glass is off unless the user turns it on. */
    const val GLASS_DEFAULT: Boolean = GeneratedThemes.GLASS_DEFAULT

    /** Shadow is off unless the user turns it on. */
    const val SHADOW_DEFAULT: Boolean = GeneratedThemes.SHADOW_DEFAULT

    val all: List<ChromekitThemeInfo> = GeneratedThemes.ALL.map { p ->
        val tokens = if (p.id == "red") ChromekitTokens else ChromekitTokens.copy(colors = p.toColors())
        ChromekitThemeInfo(p.id, p.label, p.colorName, p.lowContrast, tokens, p.glass, p.shadow)
    }

    val default: ChromekitThemeInfo = all.first { it.id == DEFAULT_ID }

    fun find(id: String?): ChromekitThemeInfo? = all.firstOrNull { it.id == id }

    fun byId(id: String?): ChromekitThemeInfo = find(id) ?: default
}
