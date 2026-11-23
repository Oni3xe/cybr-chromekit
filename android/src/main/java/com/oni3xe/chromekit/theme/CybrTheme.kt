package com.oni3xe.chromekit.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oni3xe.chromekit.ChamferShape
import com.oni3xe.chromekit.R
import com.oni3xe.chromekit.Tokens
import com.oni3xe.chromekit.Tokens.Color as T

/*
 * Chrome tokens: the chromekit 1.1 colours (the generated Tokens), type scale, spacing and shapes.
 * Layout code reads `CybrTheme.tokens`.
 */

private fun c(argb: Long) = Color(argb)

/** chromekit colour tokens, named as in tokens.json. See UI-Standard-v1.1 "Roles". */
@Immutable
data class CybrColors(
    val bg: Color,
    /** Screen fill inside the frame, fields, inputs. */
    val surface1: Color,
    /** Raised panels and dialogs. */
    val surface2: Color,
    /** Panel fill over other content (85% black). */
    val scrim: Color,
    /** Pressed frame only. Never text. */
    val redBright: Color,
    /** Commands, titles, warnings, pressed fill. */
    val red: Color,
    /** Default frame. */
    val redDim: Color,
    /** Dividers, rules, press decay. */
    val redDeep: Color,
    /** Focus ring only. Never text. */
    val cyanBright: Color,
    /** Selected, times, status, readouts. */
    val cyan: Color,
    val cyanDim: Color,
    /** Selected fill behind cyan. */
    val cyanDeep: Color,
    /** Body text. */
    val text: Color,
    /** Secondary lines, section labels, metadata. */
    val textMuted: Color,
    val disabled: Color,
    val green: Color,
    val gold: Color,
    /** Errors only, always with `!!`. */
    val error: Color,
)

@Immutable
data class CybrType(
    /** Clocks, hero numbers (display: 32sp SemiBold, 2sp tracking). */
    val display: TextStyle,
    /** Panel heads, caps (section: 14sp SemiBold, 1.5sp tracking). */
    val section: TextStyle,
    /** `// APPNAME`, panel titles (header: 16sp SemiBold, 1.5sp tracking). */
    val header: TextStyle,
    /** Buttons, tabs, warnings, caps (label: 14sp SemiBold). */
    val label: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    /** Times and readouts, JetBrains Mono. */
    val data: TextStyle,
    /** Status log, JetBrains Mono. */
    val log: TextStyle,
    val family: FontFamily,
    val mono: FontFamily,
)

/** Spacing scale 4 / 8 / 12 / 16 / 24; [l] (12) is the gutter. */
@Immutable
data class CybrSpacing(val s: Dp, val m: Dp, val l: Dp, val xl: Dp, val xxl: Dp, val rule: Dp) {
    val gutter: Dp get() = l
}

/** Square everywhere; one 6dp top-start chamfer on panels, menus, tabs and the screen frame. */
@Immutable
data class CybrShapes(
    /** Material needs a CornerBasedShape; 0dp radius is a rectangle. */
    val default: CornerBasedShape,
    val block: Shape,
    val chamfer: Shape,
)

@Immutable
data class CybrTokens(val colors: CybrColors, val type: CybrType, val spacing: CybrSpacing, val shapes: CybrShapes)

val Rajdhani = FontFamily(
    Font(R.font.rajdhani_regular, FontWeight.Normal),
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
)
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private fun w(weight: Int) = FontWeight(weight)

val ChromekitTokens = CybrTokens(
    colors = CybrColors(
        bg = c(T.BG), surface1 = c(T.SURFACE_1), surface2 = c(T.SURFACE_2),
        scrim = c(T.SCRIM),
        redBright = c(T.RED_BRIGHT), red = c(T.RED), redDim = c(T.RED_DIM), redDeep = c(T.RED_DEEP),
        cyanBright = c(T.CYAN_BRIGHT), cyan = c(T.CYAN), cyanDim = c(T.CYAN_DIM), cyanDeep = c(T.CYAN_DEEP),
        text = c(T.TEXT), textMuted = c(T.TEXT_MUTED), disabled = c(T.DISABLED),
        green = c(T.GREEN), gold = c(T.GOLD), error = c(T.ERROR),
    ),
    type = with(Tokens.Type) {
        CybrType(
            display = TextStyle(fontFamily = Rajdhani, fontWeight = w(DISPLAY_WEIGHT), fontSize = DISPLAY_SP.sp, letterSpacing = DISPLAY_TRACKING_SP.sp),
            section = TextStyle(fontFamily = Rajdhani, fontWeight = w(SECTION_WEIGHT), fontSize = SECTION_SP.sp, letterSpacing = SECTION_TRACKING_SP.sp),
            header = TextStyle(fontFamily = Rajdhani, fontWeight = w(HEADER_WEIGHT), fontSize = HEADER_SP.sp, letterSpacing = HEADER_TRACKING_SP.sp),
            label = TextStyle(fontFamily = Rajdhani, fontWeight = w(LABEL_WEIGHT), fontSize = LABEL_SP.sp, letterSpacing = LABEL_TRACKING_SP.sp),
            title = TextStyle(fontFamily = Rajdhani, fontWeight = w(TITLE_WEIGHT), fontSize = TITLE_SP.sp, letterSpacing = TITLE_TRACKING_SP.sp),
            body = TextStyle(fontFamily = Rajdhani, fontWeight = w(BODY_WEIGHT), fontSize = BODY_SP.sp, lineHeight = BODY_LINE_HEIGHT_SP.sp),
            caption = TextStyle(fontFamily = Rajdhani, fontWeight = w(CAPTION_WEIGHT), fontSize = CAPTION_SP.sp, letterSpacing = CAPTION_TRACKING_SP.sp),
            data = TextStyle(fontFamily = JetBrainsMono, fontSize = DATA_SP.sp, letterSpacing = DATA_TRACKING_SP.sp),
            log = TextStyle(fontFamily = JetBrainsMono, fontSize = LOG_SP.sp, lineHeight = LOG_LINE_HEIGHT_SP.sp),
            family = Rajdhani,
            mono = JetBrainsMono,
        )
    },
    spacing = with(Tokens.Space) { CybrSpacing(s = S4.dp, m = S8.dp, l = S12.dp, xl = S16.dp, xxl = S24.dp, rule = Tokens.Shape.BORDER_DP.dp) },
    shapes = CybrShapes(default = RoundedCornerShape(Tokens.Shape.CORNER_RADIUS_DP.dp), block = RectangleShape, chamfer = ChamferShape()),
)

@Deprecated("Renamed to ChromekitTokens", ReplaceWith("ChromekitTokens"))
val CybrcoreTokens = ChromekitTokens

val LocalCybrTokens = staticCompositionLocalOf { ChromekitTokens }

object CybrTheme {
    val tokens: CybrTokens
        @Composable @ReadOnlyComposable get() = LocalCybrTokens.current
}

/** Provides tokens and maps them onto Material 3 with every shape size set to the sharp default. */
@Composable
fun CybrTheme(tokens: CybrTokens = ChromekitTokens, content: @Composable () -> Unit) {
    val c = tokens.colors
    val scheme = darkColorScheme(
        primary = c.red, onPrimary = c.bg,
        secondary = c.cyan, onSecondary = c.bg,
        background = c.bg, onBackground = c.text,
        surface = c.surface1, onSurface = c.text,
        surfaceVariant = c.surface2, onSurfaceVariant = c.textMuted,
        outline = c.redDim, outlineVariant = c.redDeep, error = c.error, onError = c.bg,
    )
    val sharp = tokens.shapes.default
    val shapes = Shapes(extraSmall = sharp, small = sharp, medium = sharp, large = sharp, extraLarge = sharp)
    val f = tokens.type.family
    val t = Typography()
    val type = Typography(
        bodyLarge = t.bodyLarge.copy(fontFamily = f), bodyMedium = t.bodyMedium.copy(fontFamily = f),
        bodySmall = t.bodySmall.copy(fontFamily = f), labelLarge = t.labelLarge.copy(fontFamily = f),
        labelMedium = t.labelMedium.copy(fontFamily = f), labelSmall = t.labelSmall.copy(fontFamily = f),
        titleMedium = t.titleMedium.copy(fontFamily = f), titleSmall = t.titleSmall.copy(fontFamily = f),
    )
    CompositionLocalProvider(LocalCybrTokens provides tokens) {
        MaterialTheme(colorScheme = scheme, shapes = shapes, typography = type, content = content)
    }
}
