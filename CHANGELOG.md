# Changelog

## Unreleased

- Shadow (Oni3xe's device test): smooth, narrower and subtler. `shadowGlow()` / `hudPanel()` draw
  bands of at most 1px with a continuous alpha (`shadowBands()`: edge alpha easing into the outer
  alpha, then a quadratic fade), instead of 6 visible steps: a thin, bright edge with a narrow
  fade. `themes.json` shadow: `alpha` 0.45 → 0.3, `radiusDp` 14 → 8, `edgeAlpha` 0.9 (unchanged),
  `edgeWidthDp` 2 → 1.
- Fix: Shadow off drew a faint glow in glass mode (the glass glow on panels). Panels now draw no
  glow at all unless Shadow is on (`panelShadowBands()`, tested for every theme with glass on and
  off). The glass glow stays for key elements (`chromekitGlassGlow`).
- Glass no longer turns the panel frame grey: `hudPanel()` keeps `red.dim` (primary with Shadow)
  in glass mode; `borderAlpha` is left for apps' own hairlines.

- Double cut everywhere (Oni3xe): every element that had the single 6dp top-start chamfer (tabs,
  menus, dialogs, toasts, the screen frame) gets the opposite bottom-end cut too, mirrored in RTL.
  Sizes are unchanged: 6dp items stay 6dp, 10dp panels stay 10dp. `ChamferShape` (and so
  `CybrShapes.chamfer`) and `hudScreenFrame()` now use the shared `opposingChamferOutline()`; new
  `chamferCornerTriangles()` for drawn frames. No single-corner cut code is left in the kit.
- `check_standard.py`: fails on hand-built paths (`moveTo` / `lineTo` with literal coordinates) in
  the shared HUD files and in an app's optional `check.outlines` files (both profiles), so cut
  outlines always come from the shared double-cut outline.
- `render_palette_preview.py` draws tabs with the double cut. `themes.json` `panel.$comment`
  updated (no palette change). `tokens.json` is untouched: its descriptive `top-start` wording for
  the 6dp chamfer waits for the next design-vault tokens version.
- Tests: the 6dp chamfer is the double cut in every theme; corner triangles; inset outlines.

## v1.2.0 (pending Oni3xe's palette approval; not tagged)

- Tokens version 1.2.0 (`tokens.json`, `chromekit.version`, deprecated `cybrcore.version`); token
  values unchanged. No design-vault doc yet. Apps regenerate their `Tokens.kt` when they bump.
- Theme system (Oni3xe/cybr-chromekit#4): 11 dark-only themes in `tokens/themes.json` with stable
  ids and code names: KATANA · Red (default, identical to the tokens), PHANTOM · Purple,
  EMBER · Orange, ICEBREAKER · Blue, SAKURA · Pink, MATRIX · Green, NEON · Cyan, SODIUM · Amber,
  GHOST · Monochrome (faint cool secondary), NOMAD · Mocha (low contrast), CHOPPER · Noir (deep red
  and teal-grey). Palette values are a draft.
- `error` is themed per theme (KATANA keeps `#FF2BD6`) and is the one token allowed to be white
  (`#FFFFFF`, GHOST): `gen_tokens.py` and `check_standard.py` allow white for the error token only.
  `warning`, `green` and `gold` are fixed; the generator refuses overrides.
- Glass variant, off by default, layered on any theme: `"glass"` tokens in `themes.json`
  (surface alpha, border alpha, blur radius, glow), generated into `GlassStyle`; translucent
  surfaces, light hairline and glow in `hudPanel()`; background blur on API 31+ via
  `Modifier.blur` (no new dependency), translucent only below. Toggle on the Customization screen.
- Shadow, off by default and independent of glass: a primary-colour neon glow, brightest in a band
  at the panel border and fading outward (`"shadow"` tokens: alpha, radiusDp, edgeAlpha,
  edgeWidthDp; generated into `ShadowStyle`, `CybrTokens.shadow`). With glass on, Shadow also gives
  panels a 1dp primary frame. Band, glow and frame follow the panel cut outline.
- Panel shape in every theme (Shadow on or off): straight 45° cuts of 10dp on the top-start and
  bottom-end corners, mirrored in RTL (`OpposingChamferShape`, `CybrShapes.panel` / `panelCut`,
  `themes.json` `panel.chamferDp`, `tokens.json` `shape.panelCut`). Replaces the panels' single
  top-start cut; tabs, menus and the screen frame keep the 6dp top-start chamfer.
  `check_standard.py` fails on curves in the shared HUD files. Toggle on the Customization screen.
- `gen_tokens.py` generates the kit's `theme/Themes.kt` from `themes.json` when `tokens.themes` is
  set (only the kit's own `chromekit.json`); apps' generated files only change by the version.
- Runtime theme switching (`ChromekitTheme`, `ChromekitThemeState`, persisted in
  `SharedPreferences`), the theme picker and a shared `ChromekitCustomizationScreen` with an
  app-options slot (`ChromekitSection`, `ChromekitToggleRow`).
- Launcher sync client: reads cybr-launcher's read-only theme provider (theme, glass, Shadow and a
  validated wallpaper URI served by the same provider), trusted only for package
  `com.oni3xe.cybrlauncher` with a pinned signing-cert SHA-256; malformed rows fail closed. The pin is a placeholder for now,
  so sync fails closed (local theme). Manifest adds a `<queries>` entry only; no permissions.
- `CybrColors.warning`, `CybrTokens.glass` and `CybrTokens.shadow` (all defaulted, so existing code
  is unchanged).
- `tools/render_palette_preview.py`: regular and glass preview PNGs (2x) and a Markdown table.

## v1.1

- Internal identifiers now use the chromekit name: reusable workflow
  `.github/workflows/chromekit-check.yml` (job id `chromekit` in the kit's `ci.yml`),
  `chromekit.version`, checker/generator messages, and `ChromekitTokens` (a deprecated
  `CybrcoreTokens` alias remains). `.github/workflows/cybrcore-check.yml` and
  `cybrcore.version` stay as deprecated aliases so older caller paths and pin files keep
  working. `tokens/tokens.json` `"name"` is now `chromekit` and the pin path text points at
  `chromekit.version`; token values and `referenceScreen` are unchanged.

## v1.0

- cybrcore 1.1.0 tokens (`tokens/tokens.json`), unchanged from the design vault.
- `tools/gen_tokens.py`: one generator for every app, configured by the app's `chromekit.json`
  (package, Long or Int colours, optional `Tokens.Hex`, optional `cybrcore_tokens.xml`, optional
  named exceptions).
- `tools/check_standard.py`: the UI standard checker with `compose` and `views` profiles.
- `android/`: the `:chromekit` Compose library: `Hud.kt` (press latch, buttons, tabs, action bar
  and menu, header, panels, rules, text field, progress, empty state), `theme/CybrTheme.kt` and the
  generated `Tokens.kt`.
- `fonts/`: Rajdhani and JetBrains Mono with TrueType hinting removed (OFL 1.1).
- `.github/workflows/cybrcore-check.yml`: reusable check workflow.
- `HudTextField`: the block cursor skips a stale text layout instead of calling `getCursorRect`
  with an offset that layout doesn't contain, which crashed on typing (same fix as opsync #19).
