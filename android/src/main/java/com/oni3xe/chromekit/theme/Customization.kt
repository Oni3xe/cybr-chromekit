package com.oni3xe.chromekit.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.oni3xe.chromekit.HudButton
import com.oni3xe.chromekit.HudHeader
import com.oni3xe.chromekit.HudRule
import com.oni3xe.chromekit.Tokens
import com.oni3xe.chromekit.hudPanel
import com.oni3xe.chromekit.hudPress
import com.oni3xe.chromekit.rememberHudPress

/**
 * The shared Customization screen: header, the THEME section (launcher sync row + picker), then the
 * app's own sections in [appOptions] (the extension point; use [ChromekitSection] for each). In the
 * launcher it also shows the "Apply this theme to all cybr apps" checkbox.
 */
@Composable
fun ChromekitCustomizationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    state: ChromekitThemeState = rememberChromekitThemeState(),
    appOptions: @Composable ColumnScope.() -> Unit = {},
) {
    val t = CybrTheme.tokens
    Column(modifier.fillMaxSize().background(t.colors.bg)) {
        HudHeader(
            title = { Text("// CUSTOMIZATION", style = t.type.header, color = t.colors.red) },
            status = { HudButton("Back", onClick = onBack) },
        )
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(t.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(t.spacing.xl),
        ) {
            ChromekitSection("Theme") {
                ThemeSyncControls(state)
                ChromekitThemePicker(state)
                ChromekitToggleRow("Glass panels (blurred background on Android 12+)", state.glass) { state.useGlass(it) }
            }
            appOptions()
        }
    }
}

/** A titled Customization section: caps section label over a red.deep rule, then [content]. */
@Composable
fun ChromekitSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val t = CybrTheme.tokens
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.spacing.m)) {
        Text(title.uppercase(), style = t.type.section, color = t.colors.textMuted)
        HudRule()
        content()
    }
}

/** `[x] label` / `[ ] label` toggle row (no Material checkbox), 48dp touch height. */
@Composable
fun ChromekitToggleRow(label: String, checked: Boolean, modifier: Modifier = Modifier, onToggle: (Boolean) -> Unit) {
    val t = CybrTheme.tokens
    val press = rememberHudPress()
    Row(
        modifier.fillMaxWidth().heightIn(min = Tokens.Layout.BUTTON_TOUCH_HEIGHT_DP.dp)
            .hudPress(press, role = Role.Checkbox) { onToggle(!checked) }
            .semantics { stateDescription = if (checked) "On" else "Off" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(t.spacing.m),
    ) {
        Text(if (checked) "[x]" else "[ ]", style = t.type.data, color = if (checked) t.colors.cyan else t.colors.red)
        Text(label, style = t.type.body, color = t.colors.text)
    }
}

@Composable
private fun ThemeSyncControls(state: ChromekitThemeState) {
    val t = CybrTheme.tokens
    when {
        state.isLauncher -> ChromekitToggleRow("Apply this theme to all cybr apps", state.shareToAllApps) {
            state.shareWithAllApps(it)
        }
        state.followingLauncher -> Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(t.spacing.l),
        ) {
            Text("Following launcher: ${state.active.label}".uppercase(), style = t.type.label, color = t.colors.cyan, modifier = Modifier.weight(1f))
            HudButton("Use local", onClick = { state.useLauncherTheme(false) })
        }
        else -> ChromekitToggleRow("Follow the launcher's theme when it shares one", state.followLauncher) {
            state.useLauncherTheme(it)
        }
    }
}

/**
 * The theme picker: one row per theme with its accent swatches; the active theme is selected (cyan).
 * Picking a theme while following the launcher breaks away to that local theme.
 */
@Composable
fun ChromekitThemePicker(state: ChromekitThemeState = rememberChromekitThemeState(), modifier: Modifier = Modifier) {
    val t = CybrTheme.tokens
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.spacing.s)) {
        ChromekitThemes.all.forEach { theme ->
            val selected = theme.id == state.active.id
            val press = rememberHudPress()
            val c = theme.tokens.colors
            Row(
                Modifier.fillMaxWidth().heightIn(min = Tokens.Layout.LIST_ROW_MIN_HEIGHT_DP.dp)
                    .hudPress(press, role = Role.RadioButton) { state.selectTheme(theme.id) }
                    .background(if (selected) t.colors.cyanDeep else t.colors.surface1)
                    .border(Tokens.Shape.BORDER_DP.dp, if (selected) t.colors.cyan else t.colors.redDim)
                    .padding(horizontal = t.spacing.l)
                    .semantics { this.selected = selected; contentDescription = "${theme.label} theme" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(t.spacing.l),
            ) {
                Row(Modifier.hudPanel(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    listOf(c.surface2, c.red, c.redDim, c.cyan).forEach { Box(Modifier.size(16.dp).background(it)) }
                }
                Text(
                    theme.label.uppercase() + if (theme.lowContrast) " · LOW CONTRAST" else "",
                    style = t.type.label, color = if (selected) t.colors.cyan else t.colors.red, modifier = Modifier.weight(1f),
                )
                if (selected) Text("ACTIVE", style = t.type.data, color = t.colors.cyan)
            }
        }
    }
}
