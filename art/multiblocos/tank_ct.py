"""Texturas do tanque multibloco no formato das texturas conectadas (docs/TEXTURAS_CONECTADAS.md).

Por que moldura de 4 px, lisa
  tools/gen_connected_block.py preenche as bordas abertas repetindo o interior da textura com período
  16 - 2*frame. Para a emenda entre dois blocos vizinhos sumir de verdade, 2*frame precisa ser múltiplo do
  período: com --frame 2 (período 12) sempre sobra uma emenda; com --frame 4 (período 8) a repetição é exata.
  Por isso a textura base é: moldura de 4 px + um ladrilho 8x8 repetido no miolo (pixel (x,y) = ladrilho[y % 8][x % 8]).
  A moldura é de UMA cor (com um rebite no meio de cada lado): no canto 4x4, quando só uma das duas bordas está
  fechada, o script mostra o canto da textura original; se a moldura tivesse faixas (claro/escuro), a faixa do outro
  lado apareceria como um entalhe nesse canto. Gere com:
      python tools/gen_connected_block.py --name <bloco> --texture spacehorizons:block/rocket_fuel_tank_side_ct           --texture-end spacehorizons:block/rocket_fuel_tank_end --frame 4

Ladrilho 8x8: recortado da candidata 16x16 do gerador na janela (com volta) de emenda mais suave.
Anel: as cores do anel do tanque atual (rocket_fuel_tank.png): brilho em cima/esquerda, sombra azul-marinho
embaixo/direita.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
import gen_connected_block as gcb  # noqa: E402

FRAME = 4          # valor de --frame para o gen_connected_block.py
RING = {"hi": (203, 224, 234), "mid": (126, 151, 172), "lo": (78, 98, 124), "dark": (43, 58, 88)}


def best_tile(base16):
    a = np.asarray(base16.convert("RGBA")).astype(float)[..., :3]
    best = None
    for oy in range(16):
        for ox in range(16):
            t = np.roll(np.roll(a, -oy, 0), -ox, 1)[:8, :8]
            seam = np.abs(t[:, 7] - t[:, 0]).mean() + np.abs(t[7, :] - t[0, :]).mean()
            inner = np.abs(np.diff(t, axis=1)).mean() + np.abs(np.diff(t, axis=0)).mean()
            score = seam - 0.8 * inner
            if best is None or score < best[0]:
                best = (score, ox, oy)
    _, ox, oy = best
    full = np.asarray(base16.convert("RGBA"))
    return np.roll(np.roll(full, -oy, 0), -ox, 1)[:8, :8].copy()


def ring_color(x, y):
    """Cor da moldura de 4 px no pixel (x, y), ou None no miolo. Uma cor só, mais um rebite no meio de cada lado
    (fora dos cantos, para não aparecer entalhe quando só uma borda do canto está fechada)."""
    d = min(x, y, 15 - x, 15 - y)
    if d > 3:
        return None
    along = y if min(x, 15 - x) <= 3 and 4 <= y <= 11 else x
    across = min(x, 15 - x) if min(x, 15 - x) <= 3 and 4 <= y <= 11 else min(y, 15 - y)
    if 4 <= along <= 11:
        if along == 7 and across == 1:
            return RING["hi"]
        if along == 8 and across == 2:
            return RING["lo"]
    return RING["mid"]


def to_ct(base16, tile=None):
    t = best_tile(base16) if tile is None else tile
    out = np.zeros((16, 16, 4), np.uint8)
    for y in range(16):
        for x in range(16):
            c = ring_color(x, y)
            out[y, x] = (*c, 255) if c else t[y % 8, x % 8]
    return Image.fromarray(out, "RGBA")


def connected_faces(side, end, frame):
    """16 variantes de cada textura, como o gen_connected_block.py gera (t00..t15 e e00..e15)."""
    s = {m: np.asarray(gcb.variant_image(side.convert("RGBA"), frame, m)).astype(np.float32) for m in range(16)}
    e = {m: np.asarray(gcb.variant_image(end.convert("RGBA"), frame, m)).astype(np.float32) for m in range(16)}
    return s, e


def column_quads(W, D, H, side, end, frame=FRAME):
    """Quads das faces externas de uma coluna WxDxH (blocos de 16) com as texturas conectadas."""
    import iso
    s, e = connected_faces(side, end, frame)
    occ = lambda x, y, z: 0 <= x < W and 0 <= y < H and 0 <= z < D  # noqa: E731
    off = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
    quads = []
    for x in range(W):
        for y in range(H):
            for z in range(D):
                for face, (dx, dy, dz) in off.items():
                    if occ(x + dx, y + dy, z + dz):
                        continue
                    sides = gcb.SIDES[face]
                    mask = 0
                    for bit, k in ((1, "left"), (2, "right"), (4, "top"), (8, "bottom")):
                        ox, oy, oz = off[sides[k]]
                        if occ(x + ox, y + oy, z + oz):
                            mask |= bit
                    img = (e if face in ("up", "down") else s)[mask]
                    f = (x * 16, y * 16, z * 16)
                    t = (f[0] + 16, f[1] + 16, f[2] + 16)
                    p0, U, V = iso.face_quad(face, f, t)
                    quads.append((face, p0, U, V, img, (0, 0, 16, 16)))
    return quads


def column_preview(side, end, frame=FRAME, sizes=((2, 2, 8), (3, 3, 10)), scale=3):
    import iso
    view = iso.View(az=35, el=28, scale=scale)
    ims = [iso.render(column_quads(W, D, H, side, end, frame), view) for W, D, H in sizes]
    return iso.side_by_side(ims)
