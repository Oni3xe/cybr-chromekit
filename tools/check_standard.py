#!/usr/bin/env python3
"""CI checks for chromekit (v1.1), for Compose and Views apps.

Run from the consuming repo's root (the directory holding chromekit.json):

    python3 chromekit/tools/check_standard.py

Options: --config PATH (default ./chromekit.json); --root DIR (default: the config's directory).
The tokens copy is checked separately by chromekit/tools/gen_tokens.py --check.

chromekit.json, "check" section (paths relative to the repo root):

    "check": {
      "profile": "compose",         # "compose" or "views"
      "sources": ["app/src/main"],  # trees scanned for .kt and .xml files
      "generated": [...],           # generated token files (skipped)
      "exceptions": [[path_regex, line_regex, reason], ...],   # named exceptions
      # compose only:
      "hud": [...],                 # shared HUD files: indication = null allowed, no TextOverflow.Ellipsis
      "materialRules": true,        # also fail on rounded Material widgets and non-zero RoundedCornerShape
      # views only:
      "hudDirs": [...],             # directories whose Hud*.kt never set ellipsize
      "tokensOnlyLayouts": [...]    # layouts that are never repainted: no palette sentinel colours
    }

Compose profile fails on (UI-Standard-v1.1 "Enforcement"):
  - a raw colour hex (#RRGGBB, #AARRGGBB, 0xAARRGGBB, Color(0x...)) outside the generated tokens,
    except the named exceptions;
  - white or near-white: Color.White, Color.WHITE, @android:color/white, #FFFFFF;
  - FlowRow or horizontalScroll (buttons never wrap or scroll sideways; one row of buttons);
  - TextOverflow.Ellipsis in the shared buttons and tabs (the "hud" files);
  - indication = null outside the shared press modifier (the "hud" files);
  - shadow() used as a glow (Modifier.shadow, setShadowLayer);
  - FontFamily.Monospace (the data font is the bundled JetBrains Mono);
  - with materialRules: rounded Material widgets (material3 Button, Switch, Checkbox, Card, FAB,
    TextField, AlertDialog, TimePicker imports) and non-zero RoundedCornerShape.

Views profile fails on:
  - a raw colour hex (#RRGGBB, #AARRGGBB, 0xAARRGGBB) outside the generated tokens files, except
    the named exceptions;
  - white text sources: @android:color/white, Color.WHITE, #FFFFFF;
  - fontFamily="monospace" (the data font is the bundled JetBrains Mono);
  - buttons that wrap or scroll sideways (HorizontalScrollView, flexbox/flow layouts);
  - shadow glows (setShadowLayer, shadowColor): glows are drawn strokes;
  - ellipsize on the shared Hud components (labels are never truncated);
  - palette sentinel colours (@color/grey_*, search_bg, palette_option) in the tokens-only layouts,
    which are never repainted, so a sentinel would render as-is (grey or near-white).
  The Compose-only rules (indication = null, shadow(), FlowRow, TextOverflow) have no equivalent
  there; the Views checks cover the same ground.
"""
import argparse
import json
import os
import re
import sys
from pathlib import Path

# ---- Compose profile -------------------------------------------------------------------------
C_HEX = re.compile(r"(?<![\w&%])#(?:[0-9A-Fa-f]{8}|[0-9A-Fa-f]{6})\b|\b0x[0-9A-Fa-f]{8}L?\b")
C_WHITE = re.compile(r"@android:color/white|\bColor\.(White|WHITE)\b|#(?:FF)?FFFFFF\b", re.I)
C_BASE = [
    (re.compile(r"\bFlowRow\b|\bhorizontalScroll\b"), False, "buttons never wrap or scroll sideways"),
    (re.compile(r"\.shadow\(|setShadowLayer"), False, "glows are drawn strokes (drawBehind), never shadow()"),
    (re.compile(r"FontFamily\.Monospace|fontFamily=\"monospace\""), False, "use the bundled JetBrains Mono"),
]
C_MATERIAL = [
    (re.compile(r"import androidx\.compose\.material3\.(\w*Button|Switch|Checkbox|\w*Card|\w*TextField|AlertDialog|TimePicker\w*|DatePicker\w*)\b"),
     False, "no rounded Material widgets; use the chromekit HUD components"),
    (re.compile(r"RoundedCornerShape\((?!\s*(0(\.dp)?|Tokens\.Shape\.CORNER_RADIUS_DP\.dp)\s*\))"), False, "never rounded corners (chamfer or square)"),
]
# True: allowed only inside the "hud" files.
C_HUD_ONLY = [(re.compile(r"indication\s*=\s*null"), True, "indication = null only inside the shared press modifier")]

# ---- Views profile ---------------------------------------------------------------------------
V_HEX = re.compile(r"(?<![\w&])#(?:[0-9A-Fa-f]{8}|[0-9A-Fa-f]{6})\b|\b0x[0-9A-Fa-f]{8}\b")
V_WHITE = re.compile(r"@android:color/white|\bColor\.WHITE\b|#(?:FF)?FFFFFF\b", re.I)
V_RULES = [
    (re.compile(r'fontFamily="monospace"'), "use @font/jetbrains_mono_regular (bundled data font)"),
    (re.compile(r"\bHorizontalScrollView\b|flexbox|FlowLayout"), "buttons never wrap or scroll sideways"),
    (re.compile(r"setShadowLayer|shadowColor"), "glows are drawn strokes, never shadows"),
]
V_SENTINEL = re.compile(r"@color/(grey_\w+|search_bg|palette_option)\b")


def files_in(root: Path, sources: list) -> list:
    out = []
    for s in sources:
        out += [p for p in (root / s).rglob("*") if p.suffix in (".kt", ".xml") and p.is_file()]
    return sorted(out)


def excepted(exceptions: list, rel: str, line: str) -> bool:
    return any(re.search(p, rel) and re.search(l, line) for p, l, _ in exceptions)


def compose_strip(line: str, suffix: str) -> str:
    s = line.strip()
    if suffix == ".kt":
        if s.startswith(("*", "/*", "//")):
            return ""
        return re.sub(r"\s//\s.*$", "", line)
    if suffix == ".xml" and s.startswith("<!--"):
        return ""
    return line


def check_compose(root: Path, c: dict, files: list) -> list:
    problems = []
    hud = set(c.get("hud", []))
    gen = set(c.get("generated", []))
    ex = c.get("exceptions", [])
    rules = C_BASE + (C_MATERIAL if c.get("materialRules") else []) + C_HUD_ONLY
    for path in files:
        rel = path.relative_to(root).as_posix()
        if rel in gen:
            continue
        for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            code = compose_strip(line, path.suffix)
            if not code:
                continue
            if C_HEX.search(code) and not excepted(ex, rel, line):
                problems.append(f"{rel}:{n}: raw colour hex outside tokens: {line.strip()}")
            if C_WHITE.search(code) and not excepted(ex, rel, line):
                problems.append(f"{rel}:{n}: white or near-white colour: {line.strip()}")
            for rx, hud_only, why in rules:
                if rx.search(code) and not (hud_only and rel in hud):
                    problems.append(f"{rel}:{n}: {why}: {line.strip()}")
            if rel in hud and "TextOverflow.Ellipsis" in code:
                problems.append(f"{rel}:{n}: buttons and tabs are never truncated: {line.strip()}")
    return problems


def check_views(root: Path, c: dict, files: list) -> list:
    problems = []
    gen = set(c.get("generated", []))
    ex = c.get("exceptions", [])
    for path in files:
        rel = path.relative_to(root).as_posix()
        if rel in gen:
            continue
        for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            code = line.split("//", 1)[0] if path.suffix == ".kt" and not line.strip().startswith("*") else line
            if path.suffix == ".kt" and line.strip().startswith(("*", "/*")):
                continue
            if path.suffix == ".xml" and line.strip().startswith("<!--"):
                continue
            if V_HEX.search(code) and not excepted(ex, rel, line):
                problems.append(f"{rel}:{n}: raw colour hex outside tokens: {line.strip()}")
            elif V_WHITE.search(code) and not excepted(ex, rel, line):
                problems.append(f"{rel}:{n}: white or near-white colour: {line.strip()}")
            for rx, why in V_RULES:
                if rx.search(code):
                    problems.append(f"{rel}:{n}: {why}: {line.strip()}")
    for d in c.get("hudDirs", []):
        for path in (root / d).glob("Hud*.kt"):
            for n, line in enumerate(path.read_text().splitlines(), 1):
                if "ellipsize" in line and "= null" not in line:
                    problems.append(f"{path.relative_to(root).as_posix()}:{n}: Hud labels are never truncated")
    for rel in c.get("tokensOnlyLayouts", []):
        for n, line in enumerate((root / rel).read_text(encoding="utf-8").splitlines(), 1):
            if V_SENTINEL.search(line):
                problems.append(f"{rel}:{n}: tokens-only layout uses a palette sentinel; use @color/cybr_*: {line.strip()}")
    return problems


def main() -> int:
    ap = argparse.ArgumentParser(description="chromekit checks")
    ap.add_argument("--config", default="chromekit.json")
    ap.add_argument("--root", default=None)
    args = ap.parse_args()
    config = Path(os.path.abspath(args.config))
    root = Path(os.path.abspath(args.root)) if args.root else config.parent
    c = json.loads(config.read_text())["check"]
    files = files_in(root, c.get("sources", ["app/src/main"]))
    profile = c.get("profile")
    if profile == "compose":
        problems = check_compose(root, c, files)
    elif profile == "views":
        problems = check_views(root, c, files)
    else:
        print('check.profile must be "compose" or "views"')
        return 2
    if problems:
        print("chromekit standard check failed:")
        print("\n".join("  " + p for p in problems))
        return 1
    print(f"chromekit standard check passed ({len(files)} files)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
