# chromekit fonts

| File | Family | Used by |
|---|---|---|
| `res-core/font/rajdhani_regular.ttf`, `_medium`, `_semibold`, `_bold` | Rajdhani (Indian Type Foundry) | every app (UI font) |
| `res-core/font/jetbrains_mono_regular.ttf` | JetBrains Mono 2.304 (JetBrains) | every app (data font) |
| `res-mono-bold/font/jetbrains_mono_bold.ttf` | JetBrains Mono 2.304 Bold | Compose apps (the `:chromekit` module) |
| `res-rajdhani-light/font/rajdhani_light.ttf` | Rajdhani Light | cybr-launcher (Views) |

Each `res-*` directory is shaped like an Android `res/` directory, so a module adds it with
`res.srcDir(...)` and the files merge in as `R.font.<name>`, exactly as when they sat in the app.

## Licence and modification notice

Both families are licensed under the SIL Open Font License, Version 1.1: `rajdhani/OFL.txt` and
`jetbrains-mono/OFL.txt`. Neither OFL notice declares a Reserved Font Name.

**These are modified versions.** Each file is the upstream release file with its TrueType hinting
removed: the `fpgm`, `prep` and `cvt ` tables and every glyph's instructions are dropped, the `maxp`
hinting fields are zeroed and `gasp` is set for unhinted rendering. Glyph order and count, the cmap,
the horizontal metrics and every glyph outline are unchanged (checked with fontTools). The fonts
remain under the OFL; the MIT licence of this repository does not apply to them.

`SHA256SUMS` lists the exact bytes; CI checks it. These bytes are the ones shipped by
cybr-launcher #26 (Rajdhani and JetBrains Mono Regular) and cybr-alarm #4 (JetBrains Mono Bold).
