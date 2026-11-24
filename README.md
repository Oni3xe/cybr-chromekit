# chromekit

The shared UI kit for Oni3xe's Android apps. It carries UI-Standard v1.1: tokens, generator,
standard checker, fonts, the Compose HUD components and a reusable CI check. Apps consume it as a
**git submodule pinned to a tag**.

| Path | What |
|---|---|
| `tokens/tokens.json` | 1.2.0 tokens: the design vault's `tokens.v1.json` values, version bumped for the theme system (no vault doc yet) |
| `tokens/themes.json` | the 11 theme palettes and the glass variant (kit-owned; overrides on top of `tokens.json`) |
| `tools/gen_tokens.py` | generates an app's `Tokens.kt` (and optional `chromekit_tokens.xml`); the kit's `theme/Themes.kt` |
| `tools/render_palette_preview.py` | renders regular and glass preview PNGs and a Markdown table of the palettes (Pillow, no build) |
| `tools/check_standard.py` | UI standard checker, `compose` and `views` profiles |
| `fonts/` | Rajdhani and JetBrains Mono, hinting removed, SIL OFL 1.1 (see `fonts/README.md`) |
| `android/` | the `:chromekit` Android library (Compose): `Hud.kt`, `theme/CybrTheme.kt`, generated `Tokens.kt`, fonts |
| `.github/workflows/chromekit-check.yml` | reusable workflow (`on: workflow_call`) running both tools |
| `chromekit.json`, `chromekit.version` | the kit's own config, so it checks itself like an app |

Code is MIT (`LICENSE`); the fonts stay under the OFL. Rules for contributors: `AGENTS.md`.

## How an app consumes chromekit

### 1. Add the submodule, pinned to a tag

```sh
git submodule add https://github.com/Oni3xe/cybr-chromekit.git chromekit
git -C chromekit checkout v1.0          # always a tag (or, before the first tag, a reviewed commit)
git add .gitmodules chromekit
```

Clones then need `git clone --recurse-submodules` or `git submodule update --init`. To move to a
new kit release: `git -C chromekit fetch --tags && git -C chromekit checkout vX.Y`, regenerate
(step 3), commit the new pin. Never edit files inside `chromekit/` from an app repo.

### 2. Pin the tokens version and describe the app in `chromekit.json`

`chromekit.version` (repo root) holds the tokens version the app is on, e.g. `1.1.0`; the generator
refuses tokens of another version. A deprecated fallback still reads `cybrcore.version` if the new
file is missing. `chromekit.json` (repo root) configures both tools:

```json
{
  "tokens": {
    "kotlin": "app/src/main/java/com/oni3xe/example/chromekit/Tokens.kt",
    "package": "com.oni3xe.example.chromekit",
    "colorType": "long"
  },
  "check": {
    "profile": "compose",
    "sources": ["app/src/main"],
    "generated": ["app/src/main/java/com/oni3xe/example/chromekit/Tokens.kt"],
    "hud": [],
    "materialRules": true,
    "exceptions": []
  }
}
```

An existing app can keep its current Kotlin package (many still use a `cybrcore` package) so
`Tokens` imports do not change; only a new app needs the `chromekit` package in this example.

- `tokens.colorType`: `"long"` (ARGB `Long`, Compose apps) or `"int"` (ARGB `Int`, Views apps).
- `tokens.hex: true` adds `Tokens.Hex` (`"#RRGGBB"` strings); `tokens.xml: "<path>"` also writes
  `cybr_*` colour and dimen resources.
- `tokens.exceptions`: an app's named colour exceptions from `tokens.json`, e.g. cybr-map's
  `{"from": "cybr-map", "prefix": "map.", "object": "MapException", "doc": "..."}`.
- `check.hud`: app files that hold HUD components of their own (allowed `indication = null`, never
  `TextOverflow.Ellipsis`). `check.materialRules` also bans rounded Material widgets.
- `check.exceptions`: `[path_regex, line_regex, reason]` triples for named exceptions.
- Views profile keys: `hudDirs` (directories whose `Hud*.kt` never set `ellipsize`) and
  `tokensOnlyLayouts` (layouts that must not use palette sentinel colours).

The full option lists are in the docstrings of `tools/gen_tokens.py` and `tools/check_standard.py`.
Working examples: cybr-alarm, cybr-map (Compose) and cybr-launcher (Views).

### 3. Generate Tokens.kt

From the app's repo root:

```sh
python3 chromekit/tools/gen_tokens.py          # writes the files named in chromekit.json
python3 chromekit/tools/gen_tokens.py --check  # what CI runs: fail if they are out of date
python3 chromekit/tools/check_standard.py      # UI standard rules
```

The generated `Tokens.kt` stays in the app, in the app's own package, so app code (palettes, map
styles, tests) keeps using it without Compose. Commit it; never edit it by hand.

### 4. Compose apps: include the `:chromekit` module

`settings.gradle.kts`:

```kotlin
include(":chromekit")
project(":chromekit").projectDir = file("chromekit/android")
```

Root `build.gradle.kts` (same AGP version as the application plugin; the module also needs the
Compose compiler plugin, `org.jetbrains.kotlin.plugin.compose`, which Compose apps already declare):

```kotlin
plugins {
    id("com.android.application") version "9.5.0" apply false
    id("com.android.library") version "9.5.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
```

`app/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":chromekit"))
}
```

The module is built only inside an app: it declares no repositories or plugin versions of its own
and takes them (and `gradle.properties`) from the app's root project. It needs compileSdk 37,
minSdk 26 or lower in the app, Java 21 and the Compose BOM it declares (`2026.09.00`; Gradle picks
the higher BOM if the app's is newer).

Then use the kit's packages instead of local copies:

```kotlin
import com.oni3xe.chromekit.HudButton          // HudPress, hudPress, HudActionBar, HudMenuHost, ...
import com.oni3xe.chromekit.theme.CybrTheme    // CybrTheme { ... } and CybrTheme.tokens
```

What the module provides:

- `Hud.kt` (`com.oni3xe.chromekit`):
  - the press modifier (`hudPress`, `HudPress`, `PressPhase`): 140 ms latch, step-off, `KEYBOARD_TAP` haptic, glow drawn as strokes;
  - `HudButton`, `HudTab`, `HudActionBar` with the `[ ... ]` menu (`HudMenuHost`, `HudMenuState`);
  - `HudHeader`, `hudScreenFrame`, `hudPanel`, `HudRule`, `HudTextField` (block cursor), `HudProgress`, `HudEmptyState`;
  - `ChamferShape`, `LocalReduceMotion` and `systemReduceMotion`.
- `theme/CybrTheme.kt` (`com.oni3xe.chromekit.theme`): `CybrTheme`, `CybrTokens` and its colours, type, spacing and shapes, `ChromekitTokens` (deprecated alias: `CybrcoreTokens`), `Rajdhani`, `JetBrainsMono`.
- `Tokens.kt` (`com.oni3xe.chromekit`): the kit's own generated tokens; apps keep theirs.
- Themes (`com.oni3xe.chromekit.theme`, see "Themes" below): `ChromekitThemes`, `ChromekitTheme`,
  `ChromekitThemeState`, `ChromekitCustomizationScreen`, `ChromekitThemePicker`, `ChromekitSection`,
  `ChromekitToggleRow`, and the launcher sync client (`LauncherThemeContract`, `LauncherThemeClient`).
- Fonts: `R.font.rajdhani_{regular,medium,semibold,bold}` and `R.font.jetbrains_mono_{regular,bold}`, merged into the app. Delete the app's own copies.

**App-specific bits stay in the app**, for example:
- palettes that add colours (cybr-map's map style);
- screens and app-only widgets (cybr-map's `ui/Hud.kt`);
- anything whose look differs from the kit until Oni3xe greenlights the change. For example, cybr-alarm keeps its thin-cursor `HudTextField` in `cybrcore/HudTextField.kt`.

### 5. Views apps (cybr-launcher): tokens, tools and fonts only

A Views app does not include the Compose module. It uses:

- the generator with `"colorType": "int"`, plus `"hex"` and `"xml"` if it needs them. Its Views
  components (`HudPress`, `HudButton`, `HudActionBar`, ...) stay in the app;
- the checker with `"profile": "views"`;
- the fonts, as extra resource directories in `app/build.gradle`:

```groovy
android {
    sourceSets {
        main {
            res.srcDir rootProject.file('chromekit/fonts/res-core')            // Rajdhani R/M/SB/B, JetBrains Mono Regular
            res.srcDir rootProject.file('chromekit/fonts/res-rajdhani-light')  // only if the app uses Rajdhani Light
            // res.srcDir rootProject.file('chromekit/fonts/res-mono-bold')    // JetBrains Mono Bold, if used
        }
    }
}
```

The files keep their names, so `R.font.*`, `@font/*` and `getFont(...)` calls do not change. If the
app ships the OFL texts in its APK (cybr-launcher's `assets/fonts/*/`), keep those.

### 6. CI: call the reusable workflow and check out submodules

```yaml
jobs:
  chromekit:
    uses: Oni3xe/cybr-chromekit/.github/workflows/chromekit-check.yml@<full commit SHA of the tag> # pin to the tag SHA
    permissions:
      contents: read
    # with:
    #   kit-path: chromekit        # default
    #   config: chromekit.json     # default

  build:
    steps:
      - uses: actions/checkout@<SHA> # vX
        with:
          submodules: true          # the build needs chromekit/ (module and fonts)
```

Pin the reusable workflow by the tag's commit SHA with the tag as the comment. The SHA must be the
same commit as the submodule pin.

**CI access.** Until chromekit is public, both the submodule checkout and the reusable workflow
need access:

- **Reusable workflow:** a private repo's workflows can be called by other repos only after chromekit's
  *Settings > Actions > General > Access* allows repositories of the owner.
- **Submodule checkout:** this needs a token that can read chromekit. The default `GITHUB_TOKEN` of
  another repo cannot. That means either making chromekit public or adding a read-only deploy key
  as a secret on each app and passing it to `actions/checkout` (`ssh-key:`).

This is an owner decision (Oni3xe / CoS): nothing in this repo or the apps adds a key. F-Droid
builds (cybr-launcher) also need a public submodule and `submodules: yes` in their metadata.

### Wiring a new app (kiroshi-droid, opsync)

1. Add the submodule at the tag (step 1) and create `chromekit.version` and `chromekit.json` (step 2).
   For the Kotlin package, use the app's existing package if it has one, so imports of
   `Tokens` don't change.
2. Run the generator and diff the result against the app's current tokens. The values must be
   identical; only the two header comment lines may differ.
3. Compose: include the module (step 4) and point `Hud*` and `CybrTheme` imports at
   `com.oni3xe.chromekit`. Delete the app's copies only where they are identical to the kit,
   apart from comments, package and imports. Keep anything that differs in the app and list it in
   the PR as drift. Views: wire tokens, checker and fonts as in step 5.
4. Delete the duplicated fonts and check that the same `R.font` names still resolve. Compare the
   files' sha256 with `fonts/SHA256SUMS`.
5. CI (step 6): call the reusable workflow, check out submodules, and drop the old inline checks.
6. Prove there's no change in the PR body:
   - token value diff;
   - generated `Tokens.kt` diff;
   - removed component code vs the kit;
   - font hashes.

## Themes

Eleven dark-only themes, all with the Red UI's balance: Red (default, identical to `tokens.json`),
Purple, Orange, Blue, Pink, Green, Cyan, Amber, Monochrome, Mocha (low contrast) and Noir (muted
deep red with a cool teal-grey). They live in `tokens/themes.json`, one entry per theme overriding
`tokens.json` colour keys: `red.*` is the primary accent (commands, frames, titles, clock),
`cyan.*` the secondary accent (selected, readouts, labels), plus surfaces and, for Mocha and Noir,
background and text. `accent` is the special purple accent and the one themed colour next to the
alerts (Red keeps the error magenta). The alert and status colours `error`, `warning` (the Red
theme's red), `green` and `gold` are listed under `"fixed"` and the generator refuses a theme that
overrides them. Type, spacing and shapes never change: sharp corners, never white (the generator
refuses `#FFFFFF`).

**Glass variant**, off by default, layered on any theme (not a copy of each theme). `themes.json`
`"glass"` holds the defaults, and a theme may override any of them under its own `"glass"`:

| key | default | meaning |
|---|---|---|
| `enabled` | `false` | default for the user's glass toggle |
| `surfaceAlpha` | `0.55` | opacity of `surface.1`, `surface.2` and `scrim` (frosted panels) |
| `borderAlpha` | `0.22` | panel hairline: the theme's `text` colour at this alpha |
| `blurRadiusDp` | `12` | background blur, API 31+ (`Modifier.blur`/RenderEffect); none below, panels stay translucent |
| `glowAlpha` | `0.30` | soft primary-accent glow on panels and key elements (Noir: `0.45`) |
| `glowRadiusDp` | `10` | glow reach, drawn as strokes, never `shadow()` |

With glass on, `CybrTheme.tokens.glass` is non-null, the surfaces are translucent, `hudPanel()`
draws the light hairline and glow, and the app blurs its own background layer with
`Modifier.chromekitGlassBackground(tokens)`; `Modifier.chromekitGlassGlow(tokens)` adds the glow to a
key element (clock, active button). No new dependency.

Edit a palette: change `tokens/themes.json`, run `python3 tools/gen_tokens.py` (writes
`android/.../theme/Themes.kt`), then
`python3 tools/render_palette_preview.py --out DIR --tag SHA` to write `regular-SHA.png`,
`glass-SHA.png` (over a painted fog) and `themes-SHA.md` for review. The kit's CI fails if
`Themes.kt` is out of date.

In an app (Compose):

```kotlin
setContent {
    ChromekitTheme {            // instead of CybrTheme { ... }: runtime switching + launcher sync
        // From the app's settings or menu:
        ChromekitCustomizationScreen(onBack = { /* close */ }) {
            // Extension point: the app's own options, e.g.
            // ChromekitSection("Alarm") { ChromekitToggleRow("Gradual volume", on) { ... } }
        }
    }
}
```

The Customization screen also has a "Glass panels" toggle (off by default).
`ChromekitTheme` reads the persisted choice (`SharedPreferences` file `chromekit_theme`) and
recomposes immediately when it changes. `CybrTheme` keeps working unchanged (Red).

### Launcher theme sync

- cybr-launcher exposes a read-only provider (`LauncherThemeContract`: authority
  `com.oni3xe.cybrlauncher.chromekit.theme`, one row with `theme_id` and `shared`) and shows the
  "Apply this theme to all cybr apps" checkbox (`ChromekitThemeState.shareToAllApps`).
- Other apps read it on start and on change (ContentObserver). They trust it only if the
  provider's package is `com.oni3xe.cybrlauncher` **and** its single current signing certificate's
  SHA-256 equals `LAUNCHER_CERT_SHA256` (`theme/LauncherThemeSync.kt`). Otherwise, or with sync
  off, they use their local theme. The picker shows "Following launcher" with a "Use local"
  break-away while the shared theme is active.
- `LAUNCHER_CERT_SHA256` is currently `LAUNCHER_CERT_SHA256_PLACEHOLDER`: until Oni3xe pins the
  launcher's release certificate hash there, sync **fails closed** (no provider is even queried).
- Release signing stays per app. The kit's manifest adds only a `<queries>` entry for the
  launcher's package (Android 11+ package visibility). No permissions, no INTERNET, no telemetry.

## The kit's own CI

`.github/workflows/ci.yml` runs the same reusable workflow on the kit itself (`kit-path: "."`). It
also checks the font bytes against `fonts/SHA256SUMS`. There is no Gradle build of the module on its
own; it is compiled by each consuming app's CI.
