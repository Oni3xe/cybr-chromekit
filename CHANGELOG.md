# Changelog

## Unreleased

- Theme system (Oni3xe/cybr-chromekit#4): 10 dark-only themes in `tokens/themes.json` (Red default,
  identical to the 1.1 tokens; Purple, Orange, Blue, Pink, Green, Cyan, Amber, Monochrome, Mocha
  low contrast). Palette values are a draft pending Oni3xe's review.
- `gen_tokens.py` generates the kit's `theme/Themes.kt` from `themes.json` when `tokens.themes` is
  set (only the kit's own `chromekit.json`); apps' generated files are byte-identical.
- Runtime theme switching (`ChromekitTheme`, `ChromekitThemeState`, persisted in
  `SharedPreferences`), the theme picker and a shared `ChromekitCustomizationScreen` with an
  app-options slot (`ChromekitSection`, `ChromekitToggleRow`).
- Launcher sync client: reads cybr-launcher's read-only theme provider, trusted only for package
  `com.oni3xe.cybrlauncher` with a pinned signing-cert SHA-256. The pin is a placeholder for now,
  so sync fails closed (local theme). Manifest adds a `<queries>` entry only; no permissions.
- `CybrColors.warning` (defaults to the Red theme's red, so existing code is unchanged).
- `tools/render_palette_preview.py`: palette preview PNG and Markdown table for review rounds.

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
