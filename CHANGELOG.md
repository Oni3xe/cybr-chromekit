# Changelog

## v1.0 (unreleased)
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
