#!/usr/bin/env python3
"""Render the chromekit theme palettes (tokens/themes.json) as preview PNGs and a Markdown table.

For palette review rounds; no Android build involved. Needs Pillow.

    python3 tools/render_palette_preview.py --out DIR --tag SHA

writes DIR/regular-SHA.png (every theme, solid), DIR/glass-SHA.png (every theme with the glass
variant over a painted dark teal fog), DIR/regular-shadow-SHA.png and DIR/glass-shadow-SHA.png (the
same with Shadow on) and DIR/themes-SHA.md. Images are drawn at 2x so they read
at phone size. Each theme shows its colour tokens with hex codes and a mini UI: clock, date, tabs,
buttons, a list row and the alert lines.
"""
import argparse
import json
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_tokens import THEME_FIELDS, TOKENS, THEMES, resolve_themes  # noqa: E402

try:
    from PIL import Image, ImageDraw, ImageFilter, ImageFont
except ImportError:
    raise SystemExit("Pillow is required: pip install pillow")

KIT = Path(__file__).resolve().parents[1]
KEYS = [key for _, key in THEME_FIELDS]
S = 2                      # render scale (2x)
CARD_W, CARD_H, COLS, PAD = 600, 500, 3, 24
SW_COLS, SW_W, SW_H = 5, 108, 24
_fonts = {}


def font(name: str, size: int):
    key = (name, size)
    if key not in _fonts:
        hits = sorted((KIT / "fonts").rglob(name))
        try:
            _fonts[key] = ImageFont.truetype(str(hits[0]), size * S) if hits else ImageFont.load_default(size * S)
        except OSError:
            _fonts[key] = ImageFont.load_default(size * S)
    return _fonts[key]


def rgba(hex_: str, alpha: float = None) -> tuple:
    h = hex_.lstrip("#")
    c = (int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16), int(h[0:2], 16)) if len(h) == 8 else \
        (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)
    return c if alpha is None else c[:3] + (round(255 * alpha),)


def sc(*v):
    return [round(x * S) for x in v]


def chamfer(x0, y0, x1, y1, cut=6):
    return [tuple(sc(x0 + cut, y0)), tuple(sc(x1, y0)), tuple(sc(x1, y1)), tuple(sc(x0, y1)), tuple(sc(x0, y0 + cut))]


def fog(w: int, h: int) -> Image.Image:
    """A dark teal-to-black fog with faint tower silhouettes, blurred (painted, no photo)."""
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img, "RGBA")
    for y in range(h):
        t = y / h
        d.line((0, y, w, y), fill=(round(16 + 14 * (1 - t)), round(30 + 22 * (1 - t)), round(38 + 26 * (1 - t))))
    rnd = random.Random(7)
    for _ in range(26):
        x = rnd.randrange(0, w)
        bw, top = rnd.randrange(60, 220), rnd.randrange(h // 6, h // 2)
        d.rectangle((x, top, x + bw, h), fill=(rnd.randrange(8, 16), rnd.randrange(14, 24), rnd.randrange(18, 30)))
    for _ in range(40):
        x, y, r = rnd.randrange(0, w), rnd.randrange(0, h), rnd.randrange(80, 260)
        d.ellipse((x - r, y - r, x + r, y + r), fill=(28, 48, 58))
    return img.filter(ImageFilter.GaussianBlur(40 * S // 2))


def glow(img: Image.Image, box, colour: tuple, alpha: float, radius: float, outward: bool = False):
    """A soft glow around box. outward=True (Shadow) keeps it outside the edge only."""
    if alpha <= 0 or radius <= 0:
        return
    pad = round(radius * S * 2)
    x0, y0, x1, y1 = box
    layer = Image.new("RGBA", (x1 - x0 + 2 * pad, y1 - y0 + 2 * pad), (0, 0, 0, 0))
    ImageDraw.Draw(layer).rectangle((pad, pad, pad + x1 - x0, pad + y1 - y0), outline=colour[:3] + (round(255 * alpha),), width=round(radius * S / 2))
    layer = layer.filter(ImageFilter.GaussianBlur(radius * S / 2))
    if outward:
        ImageDraw.Draw(layer).rectangle((pad, pad, pad + x1 - x0, pad + y1 - y0), fill=(0, 0, 0, 0))
    img.alpha_composite(layer, (x0 - pad, y0 - pad))


def card(img: Image.Image, ox: int, oy: int, th: dict, p: dict, glass: bool, shadow: bool):
    c = {k: rgba(v) for k, v in p.items() if not k.startswith("_")}
    g = p["_glass"]
    x0, y0, x1, y1 = sc(ox, oy, ox + CARD_W, oy + CARD_H)
    if shadow:  # Shadow: as hudPanel draws it, 3 strokes of the primary colour stepping outward, fading
        sh = p["_shadow"]
        r = sh["radiusDp"] * S
        layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        for i in (1, 2, 3):
            off = r * i / 3
            ld.rectangle((x0 - off, y0 - off, x1 + off, y1 + off), outline=c["red"][:3] + (round(255 * sh["alpha"] / (i * 1.6)),),
                         width=max(1, round(r / 3)))
        img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(S)))
    if glass:
        region = img.crop((x0, y0, x1, y1)).filter(ImageFilter.GaussianBlur(g["blurRadiusDp"] * S / 2))
        img.paste(region, (x0, y0))
        glow(img, (x0, y0, x1, y1), c["red"], g["glowAlpha"] * 0.6, g["glowRadiusDp"])
        over = Image.new("RGBA", img.size, (0, 0, 0, 0))
        od = ImageDraw.Draw(over)
        od.polygon(chamfer(ox, oy, ox + CARD_W, oy + CARD_H, 10), fill=c["surface.1"][:3] + (round(255 * g["surfaceAlpha"]),),
                   outline=c["text"][:3] + (round(255 * g["borderAlpha"]),), width=S)
        img.alpha_composite(over)
    else:
        d = ImageDraw.Draw(img, "RGBA")
        d.polygon(chamfer(ox, oy, ox + CARD_W, oy + CARD_H, 10), fill=c["surface.1"], outline=c["red.dim"], width=S)
    # Everything else is drawn on an overlay so translucent strokes blend; glows go straight onto img, behind.
    ov = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)

    def text(x, y, s, f, col, anchor="la"):
        d.text(tuple(sc(ox + x, oy + y)), s, font=f, fill=col, anchor=anchor)

    name = f"{th['label']} · {th['color']}" + (" · default" if th["id"] == "red" else "") + (" · low contrast" if th.get("lowContrast") else "")
    text(18, 14, "// " + name, font("rajdhani_bold.ttf", 18), c["red"])
    text(CARD_W - 18, 18, ("GLASS" if glass else "SOLID") + (" + SHADOW" if shadow else ""), font("jetbrains_mono_regular.ttf", 10), c["cyan"], "ra")
    # Swatches with key and hex.
    for i, key in enumerate(KEYS):
        col, row = i % SW_COLS, i // SW_COLS
        sx, sy = 18 + col * (SW_W + 6), 44 + row * (SW_H + 30)
        fill = c[key] if not (glass and key in ("surface.1", "surface.2", "scrim")) else c[key][:3] + (round(255 * g["surfaceAlpha"]),)
        sw = Image.new("RGBA", tuple(sc(SW_W, SW_H)), fill)
        img.alpha_composite(sw, tuple(sc(ox + sx, oy + sy)))
        d.rectangle(sc(ox + sx, oy + sy, ox + sx + SW_W, oy + sy + SW_H), outline=c["text.muted"][:3] + (120,), width=1)
        text(sx, sy + SW_H + 2, key, font("rajdhani_semibold.ttf", 11), c["text.muted"])
        text(sx, sy + SW_H + 14, p[key].upper(), font("jetbrains_mono_regular.ttf", 9), c["text"])
    # Mini UI.
    uy = 44 + 4 * (SW_H + 30) + 4
    d.line(sc(ox + 18, oy + uy, ox + CARD_W - 18, oy + uy), fill=c["red.deep"], width=S)
    if glass:  # soft glow: the clock text blurred under itself
        layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
        ImageDraw.Draw(layer).text(tuple(sc(ox + 18, oy + uy + 8)), "6:17 PM", font=font("rajdhani_bold.ttf", 40), fill=c["red"][:3] + (round(255 * g["glowAlpha"] * 2 if g["glowAlpha"] < .5 else 255),))
        img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(g["glowRadiusDp"] * S / 2)))
    text(18, uy + 8, "6:17 PM", font("rajdhani_bold.ttf", 40), c["red"])
    text(20, uy + 56, "TUESDAY, NOV 24", font("rajdhani_semibold.ttf", 13), c["cyan"])
    # Tabs: selected cyan, others red, chamfered.
    for i, (lab, sel) in enumerate((("TML", True), ("DAY", False), ("MON", False))):
        tx = 250 + i * 112
        d.polygon(chamfer(ox + tx, oy + uy + 18, ox + tx + 100, oy + uy + 50), fill=c["cyan.deep"] if sel else None,
                  outline=c["cyan"] if sel else c["red.dim"], width=S)
        text(tx + 50, uy + 34, lab, font("rajdhani_semibold.ttf", 14), c["cyan"] if sel else c["red"], "mm")
    # List row.
    ly = uy + 82
    text(18, ly, "16:30", font("jetbrains_mono_regular.ttf", 14), c["cyan"])
    text(100, ly - 3, "CCNA", font("rajdhani_semibold.ttf", 17), c["text"])
    text(100, ly + 18, "-> 17:30 local", font("rajdhani_medium.ttf", 13), c["text.muted"])
    text(CARD_W - 18, ly, "LOCAL", font("rajdhani_semibold.ttf", 14), c["cyan"], "ra")
    d.line(sc(ox + 18, oy + ly + 42, ox + CARD_W - 18, oy + ly + 42), fill=c["red.deep"] if not glass else c["text"][:3] + (round(255 * g["borderAlpha"]),), width=S)
    # Buttons: normal, pressed, disabled.
    by = ly + 54
    for i, (lab, kind) in enumerate((("ADD", "n"), ("IMPORT", "p"), ("...", "n"), ("OFF", "d"))):
        bx = 18 + i * 142
        box = sc(ox + bx, oy + by, ox + bx + 130, oy + by + 34)
        if kind == "p":
            if glass:
                glow(img, box, c["red"], g["glowAlpha"], g["glowRadiusDp"])
            d.rectangle(box, fill=c["red"], outline=c["red.bright"], width=S)
        else:
            d.rectangle(box, outline=c["disabled"] if kind == "d" else c["red.dim"], width=S)
        col = c["bg"] if kind == "p" else c["disabled"] if kind == "d" else c["red"]
        text(bx + 65, by + 17, f"[ {lab} ]", font("rajdhani_semibold.ttf", 14), col, "mm")
    # Alerts and status (fixed) plus the themed accent.
    ay = by + 48
    f = font("rajdhani_semibold.ttf", 14)
    text(18, ay, "! LOW BATTERY", f, c["warning"])
    text(150, ay, "!! SIGNAL LOST", f, c["error"])
    text(300, ay, "OK", f, c["green"])
    text(350, ay, "* SPECIAL", f, c["gold"])
    text(18, ay + 22, "Body text: wake up at 07:30, route ready.", font("rajdhani_medium.ttf", 14), c["text"])
    img.alpha_composite(ov)


def render(themes, out: Path, glass: bool, shadow: bool):
    rows = (len(themes) + COLS - 1) // COLS
    w, h = PAD + COLS * (CARD_W + PAD), PAD + 40 + rows * (CARD_H + PAD)
    img = fog(w * S, h * S).convert("RGBA") if glass else Image.new("RGBA", (w * S, h * S), (0, 0, 0, 255))
    d = ImageDraw.Draw(img, "RGBA")
    title = "CHROMEKIT THEMES · " + ("GLASS (painted fog background)" if glass else "REGULAR") + (" + SHADOW" if shadow else "")
    d.text(tuple(sc(PAD, PAD)), title, font=font("rajdhani_bold.ttf", 22), fill=(184, 179, 182))
    for i, (th, p) in enumerate(themes):
        card(img, PAD + (i % COLS) * (CARD_W + PAD), PAD + 40 + (i // COLS) * (CARD_H + PAD), th, p, glass, shadow)
    out.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGB").save(out)
    print(f"wrote {out}")


def markdown(themes, label: str) -> str:
    doc = json.loads(THEMES.read_text())
    md = [f"# chromekit theme palettes ({label})", "",
          "Generated by `tools/render_palette_preview.py` from `tokens/themes.json` over `tokens/tokens.json`.",
          "`red.*` = primary accent, `cyan.*` = secondary accent, `error` = themed per theme (the only token that may be white).",
          f"Fixed in every theme: {', '.join('`' + k + '`' for k in doc.get('fixed', []))}.", ""]
    md.append("| token | " + " | ".join(f"{th['label']} · {th['color']} (`{th['id']}`)" for th, _ in themes) + " |")
    md.append("|---|" + "---|" * len(themes))
    for key in KEYS:
        md.append(f"| `{key}` | " + " | ".join(f"`{p[key].upper()}`" for _, p in themes) + " |")
    md += ["", "## Glass (off by default)", "", "| theme | " + " | ".join(k for k in themes[0][1]["_glass"]) + " |",
           "|---|" + "---|" * len(themes[0][1]["_glass"])]
    for th, p in themes:
        md.append(f"| {th['label']} | " + " | ".join(str(v) for v in p["_glass"].values()) + " |")
    md += ["", "## Shadow (off by default, independent of glass)", "", "| theme | " + " | ".join(themes[0][1]["_shadow"]) + " |",
           "|---|" + "---|" * len(themes[0][1]["_shadow"])]
    for th, p in themes:
        md.append(f"| {th['label']} | " + " | ".join(str(v) for v in p["_shadow"].values()) + " |")
    return "\n".join(md) + "\n"


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--out", required=True, help="output directory")
    ap.add_argument("--tag", required=True, help="file name suffix, e.g. the short head SHA")
    a = ap.parse_args()
    t = json.loads(TOKENS.read_text())
    _, themes = resolve_themes(t["color"], t["role"])
    out = Path(a.out)
    for glass in (False, True):
        for shadow in (False, True):
            name = ("glass" if glass else "regular") + ("-shadow" if shadow else "")
            render(themes, out / f"{name}-{a.tag}.png", glass=glass, shadow=shadow)
    (out / f"themes-{a.tag}.md").write_text(markdown(themes, a.tag))
    print(f"wrote {out / f'themes-{a.tag}.md'}")


if __name__ == "__main__":
    main()
