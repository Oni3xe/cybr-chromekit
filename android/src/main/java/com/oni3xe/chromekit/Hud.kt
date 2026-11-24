package com.oni3xe.chromekit

import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.oni3xe.chromekit.theme.CybrTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/*
 * chromekit 1.1 shared Compose components (UI-Standard-v1.1 "States", "Action bar and menu",
 * "Components"): the press modifier with its latch, HudButton, HudActionBar with the [ ... ] overflow,
 * the menu panel, HudHeader, HudTab, HudTextField, HudRule, HudProgress and the chamfer shape.
 * Every value comes from the generated Tokens. This is the only file allowed to use
 * `indication = null` (CI enforces it): the press visuals are drawn by the components themselves.
 */

private val Px1 = Tokens.Shape.BORDER_DP.dp

/** 45° chamfer on the top-start corner only ([cut] defaults to the 6dp token); every other corner square. */
class ChamferShape(private val cut: Dp = Tokens.Shape.CHAMFER_DP.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(min(size.width, size.height) / 2)
        val w = size.width
        val h = size.height
        val p = Path().apply {
            if (layoutDirection == LayoutDirection.Ltr) {
                moveTo(c, 0f); lineTo(w, 0f); lineTo(w, h); lineTo(0f, h); lineTo(0f, c)
            } else {
                moveTo(0f, 0f); lineTo(w - c, 0f); lineTo(w, c); lineTo(w, h); lineTo(0f, h)
            }
            close()
        }
        return Outline.Generic(p)
    }
}

/**
 * Reduce motion: the system animator duration scale is 0, or the in-app switch (the app provides it).
 * Disables the press step-off, panel steps and the scan bar sweep; keeps the press latch and haptic.
 */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun systemReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

enum class PressPhase { Normal, Pressed, Decay }

/** Latched press state of one control, read by the component to draw itself. */
@Stable
class HudPress {
    var phase by mutableStateOf(PressPhase.Normal)
        internal set
    var focused by mutableStateOf(false)
        internal set
}

@Composable
fun rememberHudPress(): HudPress = remember { HudPress() }

/**
 * The shared press modifier. Press-down snaps [press] to Pressed and ticks a KEYBOARD_TAP haptic
 * (View.performHapticFeedback: no VIBRATE permission). Pressed holds at least pressLatchMs (140) from
 * press-down even for a quick tap, then steps off: 30ms Decay (red.deep fill), then Normal (the
 * phosphor step-off, skipped under reduce motion). The latch is driven by the interaction stream, not
 * collectIsPressedAsState(), so quick or delayed presses in scrolling containers still show.
 */
fun Modifier.hudPress(
    press: HudPress,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val view = LocalView.current
    val reduce by rememberUpdatedState(LocalReduceMotion.current)
    LaunchedEffect(source) {
        var downAt = 0L
        var off: Job? = null
        source.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> {
                    off?.cancel()
                    downAt = SystemClock.uptimeMillis()
                    press.phase = PressPhase.Pressed
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    val held = SystemClock.uptimeMillis() - downAt
                    off = launch {
                        val rest = Tokens.Motion.PRESS_LATCH_MS - held
                        if (rest > 0) delay(rest)
                        if (!reduce) {
                            press.phase = PressPhase.Decay
                            delay(Tokens.Motion.PRESS_STEP_OFF_MS / 2)
                        }
                        press.phase = PressPhase.Normal
                    }
                }
                is FocusInteraction.Focus -> press.focused = true
                is FocusInteraction.Unfocus -> press.focused = false
            }
        }
    }
    if (!enabled && press.phase != PressPhase.Normal) SideEffect { press.phase = PressPhase.Normal }
    clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

/** Colours of one control in its current state (state precedence: disabled > pressed > selected > normal). */
private data class Look(val label: Color, val fill: Color, val frame: Color, val glow: Boolean, val dashed: Boolean)

@Composable
private fun look(press: HudPress, enabled: Boolean, selected: Boolean, normalFill: Color, normalLabel: Color? = null): Look {
    val c = CybrTheme.tokens.colors
    return when {
        !enabled -> Look(c.disabled, Color.Transparent, c.disabled, glow = false, dashed = true)
        press.phase == PressPhase.Pressed -> Look(c.bg, c.red, c.redBright, glow = true, dashed = false)
        press.phase == PressPhase.Decay -> Look(c.red, c.redDeep, c.redDim, glow = false, dashed = false)
        selected -> Look(c.cyan, c.cyanDeep, c.cyan, glow = false, dashed = false)
        else -> Look(normalLabel ?: c.red, normalFill, c.redDim, glow = false, dashed = false)
    }
}

/** Fill, 1dp frame (dashed when disabled), the pressed glow behind and the focus ring over. */
private fun Modifier.hudFrame(l: Look, focused: Boolean, ringColor: Color, glowColor: Color, frame: Boolean = true) = drawWithContent {
    val b = Px1.toPx()
    if (l.glow) {
        Tokens.Glow.STROKES_DP.forEachIndexed { i, o ->
            val off = o.dp.toPx()
            drawRect(glowColor.copy(alpha = Tokens.Glow.ALPHA[i]), Offset(-off, -off), Size(size.width + 2 * off, size.height + 2 * off), style = Stroke(b * 2))
        }
    }
    if (l.fill.alpha > 0f) drawRect(l.fill)
    if (frame) {
        val dash = if (l.dashed) PathEffect.dashPathEffect(floatArrayOf(Tokens.Shape.DISABLED_DASH_DP[0].dp.toPx(), Tokens.Shape.DISABLED_DASH_DP[1].dp.toPx())) else null
        drawRect(l.frame, Offset(b / 2, b / 2), Size(size.width - b, size.height - b), style = Stroke(b, pathEffect = dash))
    }
    drawContent()
    if (focused) {
        val r = Tokens.Shape.FOCUS_RING_DP.dp.toPx()
        drawRect(ringColor, Offset(r / 2, r / 2), Size(size.width - r, size.height - r), style = Stroke(r))
    }
}

/**
 * Command button `[ LABEL ]`: label type, 40dp visual height in a 48dp touch target, 12dp label
 * padding, square, full state model. [minWidth] is the action-bar slot (104, or 72 for `[ ... ]`).
 * Labels are never truncated. [fill] is the normal-state fill (scrim when it sits over other content).
 */
@Composable
fun HudButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    minWidth: Dp = 0.dp,
    fill: Color = Color.Transparent,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    val t = CybrTheme.tokens
    val press = rememberHudPress()
    val l = look(press, enabled, selected, fill)
    Box(
        modifier.heightIn(min = Tokens.Layout.BUTTON_TOUCH_HEIGHT_DP.dp).widthIn(min = minWidth)
            .hudPress(press, enabled, onClick = onClick)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.widthIn(min = minWidth).heightIn(min = Tokens.Layout.BUTTON_VISUAL_HEIGHT_DP.dp)
                .hudFrame(l, press.focused, t.colors.cyanBright, t.colors.red)
                .padding(horizontal = Tokens.Layout.BUTTON_PADDING_H_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(buttonLabel(label), style = t.type.label, color = l.label, maxLines = 1, softWrap = false)
        }
    }
}

fun buttonLabel(label: String) = "[ ${label.uppercase()} ]"

/** One command of an action bar or menu. [checked] non-null draws a `[x]` / `[ ]` toggle row in the menu. */
data class HudAction(
    val label: String,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val checked: Boolean? = null,
    val description: String? = null,
    val onClick: () -> Unit,
)

/** The `[ ... ]` menu: open flag, the anchor (the `[ ... ]` button, root coordinates) and its rows. */
@Stable
class HudMenuState {
    var open by mutableStateOf(false)
    internal var anchor by mutableStateOf<Rect?>(null)
    internal var items by mutableStateOf<List<HudAction>>(emptyList())
}

@Composable
fun rememberHudMenuState(): HudMenuState = remember { HudMenuState() }

/**
 * Bottom action bar: one row, start-aligned, 12dp gutters, 8dp gaps; never wraps, never scrolls.
 * Actions take 104dp slots (wider if a label needs it, never truncated). When they don't all fit, the
 * last slot becomes `[ ... ]` (72dp) and the rest open in the menu panel in declared order
 * ([HudMenuHost] draws it). Capacity follows the standard: 2 + `[ ... ]` at 360-411dp, 3 at 480dp.
 */
@Composable
fun HudActionBar(actions: List<HudAction>, menu: HudMenuState, modifier: Modifier = Modifier) {
    val t = CybrTheme.tokens
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val slot = Tokens.Layout.ACTION_SLOT_DP.dp
    val menuSlot = Tokens.Layout.MENU_SLOT_DP.dp
    val gap = Tokens.Layout.ACTION_GAP_DP.dp
    val pad = Tokens.Layout.BUTTON_PADDING_H_DP.dp
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = Tokens.Layout.ACTION_GUTTER_DP.dp)) {
        fun width(label: String, min: Dp): Dp = with(density) {
            val w = measurer.measure(buttonLabel(label), t.type.label).size.width.toDp() + pad * 2 + Px1 * 2
            maxOf(min, w)
        }
        val widths = actions.map { width(it.label, slot) }
        val menuW = width("...", menuSlot)
        val all = widths.fold(0.dp) { a, w -> a + w } + gap * (actions.size - 1).coerceAtLeast(0)
        val shown = if (all <= maxWidth) actions.size else {
            var used = menuW
            var k = 0
            while (k < actions.size && used + gap + widths[k] <= maxWidth) { used += gap + widths[k]; k++ }
            k
        }
        val overflow = actions.drop(shown)
        SideEffect { menu.items = overflow }
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            actions.take(shown).forEach { a ->
                HudButton(a.label, enabled = a.enabled, selected = a.selected, minWidth = slot, contentDescription = a.description, onClick = a.onClick)
            }
            if (overflow.isNotEmpty()) {
                HudButton(
                    "...", selected = menu.open, minWidth = menuSlot, contentDescription = "More actions",
                    modifier = Modifier.onGloballyPositioned { menu.anchor = it.boundsInRoot() },
                ) { menu.open = !menu.open }
            } else if (menu.open) SideEffect { menu.open = false }
        }
    }
}

/**
 * The menu panel of [HudActionBar]'s `[ ... ]`, drawn over the screen. Place it last in a Box that
 * fills the window (root coordinates). Anchored above the button, end edges aligned; >= 160dp wide;
 * chamfer; bg fill; 1dp red frame; 48dp rows with full names in red caps; red.deep dividers;
 * full-row press. Closes on selection, outside tap or back; opens and closes in 4 discrete steps
 * (panelOpenMs / panelCloseMs), instantly under reduce motion.
 */
@Composable
fun HudMenuHost(menu: HudMenuState) {
    val t = CybrTheme.tokens
    val reduce = LocalReduceMotion.current
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(menu.open, reduce) {
        val target = if (menu.open) 4 else 0
        if (reduce) { step = target; return@LaunchedEffect }
        val each = (if (menu.open) Tokens.Motion.PANEL_OPEN_MS else Tokens.Motion.PANEL_CLOSE_MS) / 4
        while (step != target) { step += if (target > step) 1 else -1; if (step != target) delay(each) else delay(0) }
    }
    val anchor = menu.anchor
    if (step == 0 || anchor == null) return
    BackHandler(enabled = menu.open) { menu.open = false }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { menu.open = false } })
    val gap = with(LocalDensity.current) { Tokens.Space.S4.dp.roundToPx() }
    Layout(content = {
        Column(
            Modifier.width(IntrinsicSize.Max).widthIn(min = Tokens.Layout.MENU_MIN_WIDTH_DP.dp)
                .drawWithContent { clipRect(top = size.height * (1f - step / 4f)) { this@drawWithContent.drawContent() } }
                .clip(t.shapes.chamfer).background(t.colors.bg).border(Px1, t.colors.red, t.shapes.chamfer),
        ) {
            menu.items.forEachIndexed { i, a ->
                if (i > 0) HudRule(Modifier.padding(horizontal = t.spacing.l))
                MenuRow(a) { menu.open = false; a.onClick() }
            }
        }
    }) { ms, c ->
        val p = ms.first().measure(c.copy(minWidth = 0, minHeight = 0))
        layout(c.maxWidth, c.maxHeight) {
            val x = (anchor.right.toInt() - p.width).coerceIn(0, (c.maxWidth - p.width).coerceAtLeast(0))
            val y = (anchor.top.toInt() - gap - p.height).coerceAtLeast(0)
            p.place(IntOffset(x, y))
        }
    }
}

@Composable
private fun MenuRow(a: HudAction, onClick: () -> Unit) {
    val t = CybrTheme.tokens
    val press = rememberHudPress()
    val l = look(press, a.enabled, a.selected, Color.Transparent)
    Row(
        Modifier.fillMaxWidth().heightIn(min = Tokens.Layout.BUTTON_TOUCH_HEIGHT_DP.dp)
            .hudPress(press, a.enabled, role = if (a.checked != null) Role.Switch else Role.Button, onClick = onClick)
            .semantics {
                a.description?.let { contentDescription = it }
                a.checked?.let { stateDescription = if (it) "On" else "Off" }
            }
            .hudFrame(l, press.focused, t.colors.cyanBright, t.colors.red, frame = false)
            .padding(horizontal = t.spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(t.spacing.m),
    ) {
        val idle = press.phase == PressPhase.Normal && a.enabled
        // One Text (spans), so the row reads as a single label: "[x] FX".
        val text = buildAnnotatedString {
            if (a.checked != null) {
                withStyle(SpanStyle(color = if (!idle) l.label else if (a.checked) t.colors.cyan else t.colors.redDim)) {
                    append(if (a.checked) "[x] " else "[ ] ")
                }
            }
            withStyle(SpanStyle(color = l.label)) { append(a.label.uppercase()) }
        }
        Text(text, style = t.type.label, maxLines = 1, softWrap = false)
    }
}

/** App header: 48dp, `// APPNAME` slot on the start, status slot (cyan caps) on the end, red.deep rule under it. */
@Composable
fun HudHeader(modifier: Modifier = Modifier, title: @Composable RowScope.() -> Unit, status: @Composable RowScope.() -> Unit) {
    val t = CybrTheme.tokens
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Tokens.Layout.HEADER_HEIGHT_DP.dp).padding(horizontal = t.spacing.gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(t.spacing.l),
        ) {
            title()
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(t.spacing.l, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = status,
            )
        }
        HudRule()
    }
}

/** Tab: 80 x 36 visual, 48 touch, chamfer, caps label without brackets; selected = cyan on cyan.deep. */
@Composable
fun HudTab(label: String, fullName: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = CybrTheme.tokens
    val press = rememberHudPress()
    val l = look(press, true, selected, Color.Transparent)
    Box(
        modifier.heightIn(min = Tokens.Layout.TAB_TOUCH_HEIGHT_DP.dp).widthIn(min = Tokens.Layout.TAB_WIDTH_DP.dp)
            .hudPress(press, role = Role.Tab, onClick = onClick).semantics { contentDescription = fullName },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.widthIn(min = Tokens.Layout.TAB_WIDTH_DP.dp).height(Tokens.Layout.TAB_VISUAL_HEIGHT_DP.dp)
                .clip(t.shapes.chamfer).background(l.fill).border(Px1, l.frame, t.shapes.chamfer)
                .padding(horizontal = Tokens.Layout.TAB_PADDING_H_MIN_DP.dp),
            contentAlignment = Alignment.Center,
        ) { Text(label.uppercase(), style = t.type.label, color = l.label, maxLines = 1, softWrap = false) }
    }
}

/**
 * Screen frame: 1dp red.dim with the 6dp top-start chamfer, drawn over the content (the corner is
 * filled with bg). Drawn, not clipped, so no path clip is redrawn on every frame.
 */
fun Modifier.hudScreenFrame(): Modifier = composed {
    val t = CybrTheme.tokens
    drawWithContent {
        drawContent()
        val b = Px1.toPx()
        val c = Tokens.Shape.CHAMFER_DP.dp.toPx()
        val ltr = layoutDirection == LayoutDirection.Ltr
        val w = size.width
        val h = size.height
        val corner = Path().apply {
            if (ltr) { moveTo(0f, 0f); lineTo(c, 0f); lineTo(0f, c) } else { moveTo(w, 0f); lineTo(w - c, 0f); lineTo(w, c) }
            close()
        }
        drawPath(corner, t.colors.bg)
        val frame = Path().apply {
            val i = b / 2
            if (ltr) {
                moveTo(c, i); lineTo(w - i, i); lineTo(w - i, h - i); lineTo(i, h - i); lineTo(i, c)
            } else {
                moveTo(i, i); lineTo(w - c, i); lineTo(w - i, c); lineTo(w - i, h - i); lineTo(i, h - i)
            }
            close()
        }
        drawPath(frame, t.colors.redDim, style = Stroke(b))
    }
}

/**
 * Panel over other content: scrim fill, 1dp red.dim frame, 6dp top-start chamfer. With glass on
 * (`CybrTheme.tokens.glass`), the fill is translucent, the frame a light hairline and a soft
 * primary-accent glow is drawn behind (strokes, never shadow()).
 */
fun Modifier.hudPanel(): Modifier = composed {
    val t = CybrTheme.tokens
    val g = t.glass
    if (g == null) clip(t.shapes.chamfer).background(t.colors.scrim).border(Px1, t.colors.redDim, t.shapes.chamfer)
    else glassGlow(t.colors.red, g.glowAlpha, g.glowRadiusDp.dp).clip(t.shapes.chamfer).background(t.colors.scrim)
        .border(Px1, t.colors.text.copy(alpha = g.borderAlpha), t.shapes.chamfer)
}

/** Soft glow behind an element: 3 strokes of [color] fading out to [radius]. No-op at alpha 0. */
fun Modifier.glassGlow(color: Color, alpha: Float, radius: Dp): Modifier = if (alpha <= 0f || radius <= 0.dp) this else drawBehind {
    val r = radius.toPx()
    for (i in 1..3) {
        val off = r * i / 3f
        drawRect(color.copy(alpha = alpha / (i * 1.6f)), Offset(-off, -off), Size(size.width + 2 * off, size.height + 2 * off), style = Stroke(r / 3f))
    }
}

/** 1dp line: red.deep divider by default (frames use red.dim). */
@Composable
fun HudRule(modifier: Modifier = Modifier, color: Color = CybrTheme.tokens.colors.redDeep) {
    Box(modifier.fillMaxWidth().height(Px1).background(color))
}

/**
 * Text field: surface.1 fill, 1dp red.dim frame, [label] in muted caps (cyan when focused), body text,
 * cyan block cursor (one character wide, blinking [Tokens.Motion.CURSOR_BLINK_MS] per phase, steady
 * under reduce motion). Focus: 2dp cyan.bright frame. Error: 1dp magenta frame (the caller shows the
 * `!!` line). The placeholder wraps rather than clip at large font scales.
 */
@Composable
fun HudTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    error: Boolean = false,
    textStyle: TextStyle = CybrTheme.tokens.type.body,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    fieldModifier: Modifier = Modifier,
) {
    val t = CybrTheme.tokens
    var focused by remember { mutableStateOf(false) }
    // Same state handling as BasicTextField(String): keep selection and composition between edits.
    var state by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val shown = if (state.text == value) state else TextFieldValue(value, TextRange(value.length))
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val measurer = rememberTextMeasurer()
    val blockW = remember(textStyle) { measurer.measure("0", textStyle).size.width.toFloat() }
    val blink = rememberBlink(focused, shown.selection)
    val frame = when { error -> t.colors.error; focused -> t.colors.cyanBright; else -> t.colors.redDim }
    val width = if (focused && !error) Tokens.Shape.FOCUS_RING_DP.dp else Px1
    Row(
        modifier.heightIn(min = Tokens.Layout.BUTTON_TOUCH_HEIGHT_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(t.spacing.m),
    ) {
        if (label != null) Text(label.uppercase(), style = t.type.label, color = if (focused) t.colors.cyan else t.colors.textMuted)
        BasicTextField(
            value = shown,
            onValueChange = { state = it; if (it.text != value) onValueChange(it.text) },
            singleLine = true,
            textStyle = textStyle.copy(color = t.colors.text),
            // The thin system cursor is hidden; the block is drawn behind the text below.
            cursorBrush = SolidColor(Color.Transparent),
            onTextLayout = { layout = it },
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().heightIn(min = Tokens.Layout.BUTTON_VISUAL_HEIGHT_DP.dp)
                        .background(t.colors.surface1).border(width, frame).padding(horizontal = t.spacing.m),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // Placeholder and field share a top-start box, so the cursor sits on the placeholder's
                    // first line when it wraps at large font scales.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
                        if (value.isEmpty() && placeholder != null) Text(placeholder, style = textStyle, color = t.colors.textMuted)
                        Box(
                            Modifier.drawBehind {
                                val l = layout ?: return@drawBehind
                                if (!focused || !blink.value || !shown.selection.collapsed) return@drawBehind
                                // The selection moves before onTextLayout. A stale layout throws from getCursorRect.
                                val offset = cursorOffsetForLayout(shown.selection.start, shown.text, l.layoutInput.text.text)
                                    ?: return@drawBehind
                                val r = l.getCursorRect(offset)
                                // Single-line fields scroll to keep the cursor in view: keep the block inside too.
                                val x = r.left.coerceIn(0f, (size.width - blockW).coerceAtLeast(0f))
                                drawRect(t.colors.cyan, Offset(x, r.top), Size(blockW, r.height))
                            },
                        ) { inner() }
                    }
                }
            },
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused }.then(fieldModifier),
        )
    }
}

/**
 * Cursor blink: true / false every [Tokens.Motion.CURSOR_BLINK_MS] while [active], restarting on
 * (visible) whenever [key] changes; always on under reduce motion. Read it in a draw lambda, so a
 * blink only redraws.
 */
@Composable
private fun rememberBlink(active: Boolean, key: Any?): androidx.compose.runtime.State<Boolean> {
    val reduce = LocalReduceMotion.current
    val on = remember { mutableStateOf(true) }
    LaunchedEffect(active, key, reduce) {
        on.value = true
        if (!active || reduce) return@LaunchedEffect
        while (true) {
            delay(Tokens.Motion.CURSOR_BLINK_MS)
            on.value = !on.value
        }
    }
    return on
}

/** Empty state: [text] (e.g. `// NO PINS`) in text.muted, ending in a blinking cyan block cursor. */
@Composable
fun HudEmptyState(text: String, modifier: Modifier = Modifier, style: TextStyle = CybrTheme.tokens.type.data) {
    val t = CybrTheme.tokens
    val blink = rememberBlink(true, Unit)
    val cursor = "cursor"
    Text(
        buildAnnotatedString { append(text); append(" "); appendInlineContent(cursor, "_") },
        style = style, color = t.colors.textMuted, modifier = modifier,
        inlineContent = mapOf(cursor to InlineTextContent(Placeholder(0.6.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
            Box(Modifier.fillMaxSize().drawBehind { if (blink.value) drawRect(t.colors.cyan) })
        }),
    )
}

/**
 * Loading / copy progress: a 2dp scanning bar (cyan on red.deep) with a cyan data label. [fraction]
 * null = unknown size: the bar sweeps (scanBarSweepMs), static under reduce motion.
 */
@Composable
fun HudProgress(fraction: Float?, label: String, modifier: Modifier = Modifier) {
    val t = CybrTheme.tokens
    val reduce = LocalReduceMotion.current
    var sweep by remember { mutableFloatStateOf(0f) }
    if (fraction == null && !reduce) {
        LaunchedEffect(Unit) {
            val start = SystemClock.uptimeMillis()
            while (true) {
                sweep = ((SystemClock.uptimeMillis() - start) % Tokens.Motion.SCAN_BAR_SWEEP_MS) / Tokens.Motion.SCAN_BAR_SWEEP_MS.toFloat()
                delay(33)
            }
        }
    }
    Column(
        modifier.fillMaxWidth().hudPanel().padding(horizontal = t.spacing.l, vertical = t.spacing.m)
            .semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(t.spacing.s),
    ) {
        Text(if (fraction == null && reduce) "$label · LOADING…" else label, style = t.type.data, color = t.colors.cyan)
        Box(
            Modifier.fillMaxWidth().height(2.dp).background(t.colors.redDeep).drawBehind {
                if (fraction != null) drawRect(t.colors.cyan, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height))
                else if (!reduce) drawRect(t.colors.cyan, Offset(size.width * (sweep * 1.25f - 0.25f), 0f), Size(size.width * 0.25f, size.height))
            },
        )
    }
}
