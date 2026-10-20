"""Folha de candidatas por textura: variantes 1-3 do texture.py (redução por voto) e, para blocos,
4-6 = os mesmos brutos reduzidos por média de área (reduce16.py), cada uma em mosaico 2x2.

Uso: python art/textures/candidates.py <nome> [<nome>...]   -> art/_work/cand/<nome>.png
"""
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw

WORK = Path("art/_work/out/_texturas")
CAND = Path("art/_work/cand")
HERE = Path(__file__).resolve().parent


def tile(im):
    t = Image.new("RGBA", (im.width * 2, im.height * 2))
    for x in (0, im.width):
        for y in (0, im.height):
            t.paste(im, (x, y))
    return t


def main():
    CAND.mkdir(parents=True, exist_ok=True)
    for name in sys.argv[1:]:
        d = WORK / name
        imgs = []
        for i in (1, 2, 3):
            v = d / "variantes" / f"{i}.png"
            if v.exists():
                imgs.append((str(i), Image.open(v).convert("RGBA")))
        is_block = all(im.getchannel("A").getextrema() == (255, 255) for _, im in imgs)
        if is_block:
            for i in (1, 2, 3):
                raw = d / "raw" / f"raw_{i}.png"
                if raw.exists():
                    out = d / f"area_{i}.png"
                    subprocess.run([sys.executable, str(HERE / "reduce16.py"), str(raw), str(out)], check=True)
                    imgs.append((str(i + 3), Image.open(out).convert("RGBA")))
        s, pad = 6, 10
        cells = [(lbl, (tile(im) if is_block else im).resize(((32 if is_block else 16) * s,) * 2, Image.NEAREST))
                 for lbl, im in imgs]
        w = sum(c.width for _, c in cells) + pad * (len(cells) + 1)
        h = max(c.height for _, c in cells) + pad * 2 + 14
        sheet = Image.new("RGBA", (w, h), (70, 70, 76, 255))
        dr = ImageDraw.Draw(sheet)
        x = pad
        for lbl, c in cells:
            sheet.alpha_composite(c, (x, pad))
            dr.text((x, pad + c.height + 2), lbl, fill="white")
            x += c.width + pad
        sheet.save(CAND / f"{name}.png")
        print(CAND / f"{name}.png")


if __name__ == "__main__":
    main()
