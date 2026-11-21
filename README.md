# chromekit

The shared **cybrcore** UI kit for Oni3xe's Android apps. *cybrcore* is the style (UI-Standard
v1.1); *chromekit* is the kit that carries it: tokens, generator, standard checker, fonts, the
Compose HUD components and a reusable CI check. Apps consume it as a **git submodule pinned to a
tag**.

| Path | What |
|---|---|
| `tokens/tokens.json` | cybrcore 1.1.0 tokens, byte-identical to the design vault's `tokens.v1.json` |
| `tools/gen_tokens.py` | generates an app's `Tokens.kt` (and optional `cybrcore_tokens.xml`) |
| `tools/check_standard.py` | UI standard checker, `compose` and `views` profiles |
| `fonts/` | Rajdhani and JetBrains Mono, hinting removed, SIL OFL 1.1 (see `fonts/README.md`) |
| `android/` | the `:chromekit` Android library (Compose): `Hud.kt`, `theme/CybrTheme.kt`, generated `Tokens.kt`, fonts |
| `.github/workflows/cybrcore-check.yml` | reusable workflow (`on: workflow_call`) running both tools |
| `chromekit.json`, `cybrcore.version` | the kit's own config, so it checks itself like an app |

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

### 2. Pin the cybrcore version and describe the app in `chromekit.json`

`cybrcore.version` (repo root) holds the cybrcore version the app is on, e.g. `1.1.0`; the generator
refuses tokens of another version. `chromekit.json` (repo root) configures both tools:

```json
{
  "tokens": {
    "kotlin": "app/src/main/java/com/oni3xe/example/cybrcore/Tokens.kt",
    "package": "com.oni3xe.example.cybrcore",
    "colorType": "long"
  },
  "check": {
    "profile": "compose",
    "sources": ["app/src/main"],
    "generated": ["app/src/main/java/com/oni3xe/example/cybrcore/Tokens.kt"],
    "hud": [],
    "materialRules": true,
    "exceptions": []
  }
}
```

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
- `theme/CybrTheme.kt` (`com.oni3xe.chromekit.theme`): `CybrTheme`, `CybrTokens` and its colours, type, spacing and shapes, `Rajdhani`, `JetBrainsMono`.
- `Tokens.kt` (`com.oni3xe.chromekit`): the kit's own generated tokens; apps keep theirs.
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
  cybrcore:
    uses: Oni3xe/cybr-chromekit/.github/workflows/cybrcore-check.yml@<full commit SHA of the tag> # v1.0
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

1. Add the submodule at the tag (step 1) and create `cybrcore.version` and `chromekit.json` (step 2).
   For the Kotlin package, use the app's existing `cybrcore` package if it has one, so imports of
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

## The kit's own CI

`.github/workflows/ci.yml` runs the same reusable workflow on the kit itself (`kit-path: "."`). It
also checks the font bytes against `fonts/SHA256SUMS`. There is no Gradle build of the module on its
own; it is compiled by each consuming app's CI.
