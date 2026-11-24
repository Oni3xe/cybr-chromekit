#!/usr/bin/env python3
"""Render the chromekit theme palettes (tokens/themes.json) as a preview PNG and a Markdown table.

For review rounds on the palette; no Android build involved. Needs Pillow.

    python3 tools/render_palette_preview.py --png preview.png --md themes.md

Each theme gets its colour tokens as labelled swatches and a small HUD sample on its own
background: a command button, a pressed button, a selected highlight, a chamfered frame, body text,
a warning and an error line.
"""
import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_tokens import THEME_FIELDS, TOKENS, resolve_themes  # noqa: E402

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    raise SystemExit("Pillow is required: pip install pillow")

KIT = Path(__file__).resolve().parents[1]
FONT_DIR = KIT / "fonts"
KEYS = [key for _, key in THEME_FIELDS]
SW, SH, GAP, COLS = 118, 44, 8, 10
LABEL_H = 34
SAMPLE_W = 560
PAD = 24


def font(name: str, size: int):
    hits = sorted(FONT_DIR.rglob(name))
    try:
        return ImageFont.truetype(str(hits[0]), size) if hits else ImageFont.load_default(size)
    except OSError:
        return ImageFont.load_default(size)


def rgba(hex_: str) -> tuple:
    h = hex_.lstrip("#")
    if len(h) == 8:  # #AARRGGBB
        return int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16), int(h[0:2], 16)
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255


def rgb(hex_: str) -> tuple:
    return rgba(hex_)[:3]


def chamfer_frame(d, box, colour, fill=None, cut=8):
    x0, y0, x1, y1 = box
    pts = [(x0 + cut, y0), (x1, y0), (x1, y1), (x0, y1), (x0, y0 + cut)]
    d.polygon(pts, fill=fill, outline=colour)


def sample(d, x, y, h, p, f):
    """The HUD sample for palette p in a SAMPLE_W x h box at (x, y)."""
    c = {k: rgb(v) for k, v in p.items()}
    d.rectangle((x, y, x + SAMPLE_W, y + h), fill=c["bg"])
    chamfer_frame(d, (x + 6, y + 6, x + SAMPLE_W - 6, y + h - 6), c["red.dim"], fill=c["surface.1"])
    d.text((x + 20, y + 14), "// CYBR-APP", font=f["hdr"], fill=c["red"])
    d.text((x + SAMPLE_W - 20, y + 16), "ONLINE 21:47", font=f["mono"], fill=c["cyan"], anchor="ra")
    d.line((x + 12, y + 42, x + SAMPLE_W - 12, y + 42), fill=c["red.deep"], width=1)
    by = y + 52
    d.rectangle((x + 20, by, x + 150, by + 34), outline=c["red.dim"])
    d.text((x + 85, by + 17), "[ ARM ]", font=f["label"], fill=c["red"], anchor="mm")
    d.rectangle((x + 162, by, x + 292, by + 34), fill=c["red"], outline=c["red.bright"])
    d.text((x + 227, by + 17), "[ PRESSED ]", font=f["label"], fill=c["bg"], anchor="mm")
    d.rectangle((x + 304, by, x + 434, by + 34), fill=c["cyan.deep"], outline=c["cyan"])
    d.text((x + 369, by + 17), "[ SELECTED ]", font=f["label"], fill=c["cyan"], anchor="mm")
    d.rectangle((x + 446, by, x + 540, by + 34), outline=c["disabled"])
    d.text((x + 493, by + 17), "[ OFF ]", font=f["label"], fill=c["disabled"], anchor="mm")
    ty = by + 46
    d.text((x + 20, ty), "Body text: wake up at 07:30, route ready.", font=f["body"], fill=c["text"])
    d.text((x + 20, ty + 22), "Muted: last sync 2 min ago", font=f["body"], fill=c["text.muted"])
    d.text((x + 20, ty + 44), "! LOW BATTERY", font=f["label"], fill=c["warning"])
    d.text((x + 190, ty + 44), "!! SIGNAL LOST", font=f["label"], fill=c["error"])
    d.text((x + 360, ty + 44), "OK", font=f["label"], fill=c["green"])
    d.text((x + 400, ty + 44), "SPECIAL", font=f["label"], fill=c["gold"])


def render(themes, out: Path):
    f = {
        "title": font("rajdhani_bold.ttf", 26), "hdr": font("rajdhani_semibold.ttf", 18),
        "label": font("rajdhani_semibold.ttf", 16), "body": font("rajdhani_medium.ttf", 17),
        "key": font("rajdhani_semibold.ttf", 15), "mono": font("jetbrains_mono_regular.ttf", 13),
    }
    rows = (len(KEYS) + COLS - 1) // COLS
    sw_h = rows * (SH + LABEL_H + GAP)
    block_h = max(sw_h, 190) + 44
    width = PAD * 3 + COLS * (SW + GAP) + SAMPLE_W
    height = PAD + 40 + len(themes) * (block_h + PAD)
    img = Image.new("RGB", (width, height), (0, 0, 0))
    d = ImageDraw.Draw(img)
    grey, dim = (184, 179, 182), (85, 82, 90)
    d.text((PAD, PAD), "CHROMEKIT THEMES · PALETTE PREVIEW", font=f["title"], fill=grey)
    y = PAD + 44
    for th, pal in themes:
        d.line((PAD, y - 6, width - PAD, y - 6), fill=dim)
        name = th["label"].upper() + ("  · DEFAULT" if th["id"] == "red" else "") + ("  · LOW CONTRAST" if th.get("lowContrast") else "")
        d.text((PAD, y), name, font=f["title"], fill=rgb(pal["red"]))
        sy = y + 38
        for i, key in enumerate(KEYS):
            col, row = i % COLS, i // COLS
            sx, syy = PAD + col * (SW + GAP), sy + row * (SH + LABEL_H + GAP)
            r, g, b, a = rgba(pal[key])
            k = a / 255  # show alpha colours (scrim) over the theme bg
            bg = rgb(pal["bg"])
            fillc = tuple(round(v * k + bgv * (1 - k)) for v, bgv in zip((r, g, b), bg))
            d.rectangle((sx, syy, sx + SW, syy + SH), fill=fillc, outline=dim)
            d.text((sx, syy + SH + 3), key, font=f["key"], fill=grey)
            d.text((sx, syy + SH + 18), pal[key].upper(), font=f["mono"], fill=grey)
        sample(d, PAD * 2 + COLS * (SW + GAP), y + 38, block_h - 44, pal, f)
        y += block_h + PAD
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)


def markdown(themes, label: str) -> str:
    md = [f"# chromekit theme palettes ({label})", "",
          "Generated by `tools/render_palette_preview.py` from `tokens/themes.json` over `tokens/tokens.json`.",
          "`red.*` = primary accent (commands, frames), `cyan.*` = secondary accent (selected, readouts).", ""]
    md.append("| token | " + " | ".join(th["label"] for th, _ in themes) + " |")
    md.append("|---|" + "---|" * len(themes))
    for key in KEYS:
        md.append(f"| `{key}` | " + " | ".join(f"`{p[key].upper()}`" for _, p in themes) + " |")
    md.append("")
    for th, _ in themes:
        md.append(f"- **{th['label']}** (`{th['id']}`)" + (" low contrast" if th.get("lowContrast") else "")
                  + (" default, identical to tokens.json" if th["id"] == "red" else ""))
    return "\n".join(md) + "\n"


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--png", required=True)
    ap.add_argument("--md", default=None)
    ap.add_argument("--label", default="tokens/themes.json")
    a = ap.parse_args()
    t = json.loads(TOKENS.read_text())
    _, themes = resolve_themes(t["color"], t["role"])
    render(themes, Path(a.png))
    print(f"wrote {a.png}")
    if a.md:
        Path(a.md).parent.mkdir(parents=True, exist_ok=True)
        Path(a.md).write_text(markdown(themes, a.label))
        print(f"wrote {a.md}")


if __name__ == "__main__":
    main()
