"""Copia as variantes escolhidas (picks.json) para assets/ e deriva as frentes acesas (_on).

Uso (raiz do repo): python art/textures/apply_picks.py
As frentes acesas saem da frente apagada escolhida, para as duas ficarem idênticas fora da
parte que acende: refinery_on pinta o interior escuro da fornalha com um degradê de fogo;
oxygen_compressor_on clareia a grade da ventoinha e os tons ciano (luz de funcionamento).
"""
import json
import shutil
from collections import deque
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
WORK = Path("art/_work/out/_texturas")
ASSETS = Path("src/main/resources/assets/spacehorizons/textures")


def source(name, n):
    d = WORK / name
    return d / "variantes" / f"{n}.png" if n <= 3 else d / f"area_{n - 3}.png"


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def dark_region(im, box):
    """Pixels escuros conectados ao centro da caixa (x0, y0, x1, y1): o vão da fornalha / grade."""
    px = im.load()
    x0, y0, x1, y1 = box
    seeds = [(x, y) for x in range(x0, x1) for y in range(y0, y1)]
    darkest = min(lum(px[p]) for p in seeds)
    limit = darkest + 28
    region = set()
    q = deque(p for p in seeds if lum(px[p]) <= limit)
    while q:
        p = q.popleft()
        if p in region:
            continue
        x, y = p
        if not (0 <= x < im.width and 0 <= y < im.height) or lum(px[p]) > limit:
            continue
        if not (x0 - 3 <= x < x1 + 3 and y0 - 3 <= y < y1 + 3):
            continue
        region.add(p)
        q.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
    return region


def refinery_on(src, dst):
    im = Image.open(src).convert("RGBA")
    px = im.load()
    reg = dark_region(im, (5, 7, 11, 12))
    if not reg:
        raise SystemExit("refinery: não achei o vão da fornalha")
    ys = [y for _, y in reg]
    top, bot = min(ys), max(ys)
    fire = [(255, 236, 140), (255, 196, 64), (247, 130, 30), (214, 74, 22), (140, 38, 26)]
    for x, y in reg:
        t = (bot - y) / max(1, bot - top)  # 0 embaixo (mais quente) .. 1 em cima
        idx = min(len(fire) - 1, int(t * len(fire)))
        if (x + y) % 5 == 0 and idx < len(fire) - 1:
            idx += 1  # tremulação
        px[x, y] = fire[idx] + (255,)
    im.save(dst)


def compressor_on(src, dst):
    im = Image.open(src).convert("RGBA")
    px = im.load()
    reg = dark_region(im, (6, 6, 10, 10))
    for x, y in reg:
        r, g, b, a = px[x, y]
        px[x, y] = (min(255, r + 10), min(255, g + 70), min(255, b + 80), a)
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if g > r + 30 and b > r + 30:  # tons ciano: acende
                px[x, y] = (min(255, r + 60), min(255, g + 60), min(255, b + 40), a)
    im.save(dst)


def main():
    picks = json.loads((HERE / "picks.json").read_text(encoding="utf-8"))
    for kind in ("block", "item"):
        for name, n in picks[kind].items():
            dst = ASSETS / kind / f"{name}.png"
            shutil.copyfile(source(name, n), dst)
            print(dst)
    refinery_on(ASSETS / "block/refinery.png", ASSETS / "block/refinery_on.png")
    compressor_on(ASSETS / "block/oxygen_compressor.png", ASSETS / "block/oxygen_compressor_on.png")
    print("frentes acesas geradas")


if __name__ == "__main__":
    main()
