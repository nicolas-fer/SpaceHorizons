"""Candidatas 16x16 de cada job de textura dos multiblocos.

Para cada job (art/_work/multiblocos/out/_texturas/<job>/) junta:
  - as variantes do texture.py (redução por voto, variantes/N.png);
  - os mesmos brutos reduzidos por média de área (BOX) e quantizados a 7 cores (método do
    art/textures/reduce16.py), que preserva melhor painéis e juntas quando o gerador desenha 50 a 80 células.
Descarta as quase lisas (menos de 3 cores) e as repetidas. A ordem é estável (voto 1..N, depois BOX 1..N).
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageEnhance

sys.path.insert(0, str(Path.home() / ".claude/skills/minecraft-entity/scripts"))
import pixelize as px  # noqa: E402

WORK = Path("art/_work/multiblocos/out/_texturas")


def box16(raw_path, colors=7):
    img = np.asarray(Image.open(raw_path).convert("RGB")).astype(float)
    bg = px.corner_color(img)
    work = px.crop_border(img, bg, 48)
    im = Image.fromarray(np.clip(work, 0, 255).astype(np.uint8)).resize((16, 16), Image.BOX)
    im = ImageEnhance.Contrast(im).enhance(1.15)
    im = ImageEnhance.Color(im).enhance(1.1)
    q = im.quantize(colors=colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    return q.convert("RGBA")


def candidates(job):
    d = WORK / job
    out = []
    for p in sorted((d / "variantes").glob("*.png"), key=lambda p: p.stem):
        out.append((f"voto {p.stem}", Image.open(p).convert("RGBA").resize((16, 16), Image.NEAREST)))
    for p in sorted((d / "raw").glob("raw_*.png")):
        out.append((f"BOX {p.stem.split('_')[1]}", box16(p)))
    seen, keep = set(), []
    for label, im in out:
        a = np.asarray(im)
        key = a.tobytes()
        if key in seen:
            continue
        if len({tuple(c) for c in a.reshape(-1, 4)[:, :3]}) < 3:
            continue
        seen.add(key)
        keep.append((label, im))
    return keep
