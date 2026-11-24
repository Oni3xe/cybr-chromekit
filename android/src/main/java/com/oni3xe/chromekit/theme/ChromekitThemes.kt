package com.oni3xe.chromekit.theme

import androidx.compose.ui.graphics.Color

/** One theme's resolved colours as ARGB longs; generated from tokens/themes.json into Themes.kt. */
internal data class ThemePalette(
    val id: String, val label: String, val lowContrast: Boolean,
    val bg: Long, val surface1: Long, val surface2: Long, val scrim: Long,
    val redBright: Long, val red: Long, val redDim: Long, val redDeep: Long,
    val cyanBright: Long, val cyan: Long, val cyanDim: Long, val cyanDeep: Long,
    val text: Long, val textMuted: Long, val disabled: Long,
    val green: Long, val gold: Long, val error: Long, val warning: Long,
) {
    fun toColors() = CybrColors(
        bg = Color(bg), surface1 = Color(surface1), surface2 = Color(surface2), scrim = Color(scrim),
        redBright = Color(redBright), red = Color(red), redDim = Color(redDim), redDeep = Color(redDeep),
        cyanBright = Color(cyanBright), cyan = Color(cyan), cyanDim = Color(cyanDim), cyanDeep = Color(cyanDeep),
        text = Color(text), textMuted = Color(textMuted), disabled = Color(disabled),
        green = Color(green), gold = Color(gold), error = Color(error), warning = Color(warning),
    )
}

/** A selectable chromekit theme. [tokens] is the full token set (type, spacing, shapes unchanged). */
class ChromekitThemeInfo internal constructor(
    val id: String,
    val label: String,
    /** Softer contrast by design (Mocha). */
    val lowContrast: Boolean,
    val tokens: CybrTokens,
)

/**
 * The theme registry: all themes from tokens/themes.json, dark only, in picker order. Red is the
 * default and identical to [ChromekitTokens]. Unknown or missing ids resolve to the default.
 */
object ChromekitThemes {
    const val DEFAULT_ID: String = GeneratedThemes.DEFAULT_ID

    val all: List<ChromekitThemeInfo> = GeneratedThemes.ALL.map { p ->
        val tokens = if (p.id == "red") ChromekitTokens else ChromekitTokens.copy(colors = p.toColors())
        ChromekitThemeInfo(p.id, p.label, p.lowContrast, tokens)
    }

    val default: ChromekitThemeInfo = all.first { it.id == DEFAULT_ID }

    fun find(id: String?): ChromekitThemeInfo? = all.firstOrNull { it.id == id }

    fun byId(id: String?): ChromekitThemeInfo = find(id) ?: default
}
