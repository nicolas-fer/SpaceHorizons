"""Renderizador isométrico simples (numpy + Pillow) para prévias de modelos de bloco Java e de colunas de blocos.

Sem dependência do Blockbench: projeta cada face (um retângulo no espaço) com z-buffer e amostra a textura
por vizinho mais próximo (pixel art). Usado por make_tank_ct.py (prévia das colunas conectadas) e por
build_escolha.py (renders dos motores com cada textura).

Convenção de UV igual à do vanilla (a mesma de art/models/build_models.py e tools/gen_connected_block.py).
Elements com "rotation" não são suportados (os modelos dos multiblocos não usam).
"""
import math

import numpy as np
from PIL import Image

SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.62, "west": 0.62}
NORMAL = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1),
          "east": (1, 0, 0), "west": (-1, 0, 0)}


def face_quad(face, f, t):
    """(P0, U, V): canto da textura (u0,v0) e os vetores até (u1,v0) e (u0,v1)."""
    x1, y1, z1 = f
    x2, y2, z2 = t
    if face == "north":
        return (x2, y2, z1), (x1 - x2, 0, 0), (0, y1 - y2, 0)
    if face == "south":
        return (x1, y2, z2), (x2 - x1, 0, 0), (0, y1 - y2, 0)
    if face == "east":
        return (x2, y2, z2), (0, 0, z1 - z2), (0, y1 - y2, 0)
    if face == "west":
        return (x1, y2, z1), (0, 0, z2 - z1), (0, y1 - y2, 0)
    if face == "up":
        return (x1, y2, z1), (x2 - x1, 0, 0), (0, 0, z2 - z1)
    return (x1, y1, z2), (x2 - x1, 0, 0), (0, 0, z1 - z2)  # down


class View:
    def __init__(self, az=45.0, el=30.0, scale=6.0):
        a, e = math.radians(az), math.radians(el)
        self.d = np.array([math.cos(e) * math.sin(a), math.sin(e), math.cos(e) * math.cos(a)])
        r = np.array([self.d[2], 0.0, -self.d[0]])
        self.r = r / np.linalg.norm(r)
        self.u = np.cross(self.d, self.r)
        self.scale = scale

    def proj(self, p):
        p = np.asarray(p, float)
        return np.array([p @ self.r, -(p @ self.u)]) * self.scale, p @ self.d


def render(quads, view, pad=8, bg=None):
    """quads: lista de (face, P0, U, V, img RGBA numpy HxWx4, (u0,v0,u1,v1) em 0..16).
    Devolve uma imagem RGBA recortada ao conteúdo."""
    vis = []
    for q in quads:
        if np.dot(NORMAL[q[0]], view.d) > 1e-6:
            vis.append(q)
    if not vis:
        return Image.new("RGBA", (16, 16))
    pts = []
    for face, p0, U, V, *_ in vis:
        p0 = np.asarray(p0, float)
        for c in (p0, p0 + U, p0 + V, p0 + np.asarray(U) + V):
            pts.append(view.proj(c)[0])
    pts = np.array(pts)
    mn = np.floor(pts.min(0)) - pad
    mx = np.ceil(pts.max(0)) + pad
    W, H = int(mx[0] - mn[0]), int(mx[1] - mn[1])
    col = np.zeros((H, W, 4), np.float32)
    zb = np.full((H, W), -1e9, np.float32)
    for face, p0, U, V, img, uv in vis:
        p0 = np.asarray(p0, float)
        U, V = np.asarray(U, float), np.asarray(V, float)
        s0, z0 = view.proj(p0)
        su, zu = view.proj(p0 + U)
        sv, zv = view.proj(p0 + V)
        s0, su, sv = s0 - mn, su - mn - (s0 - mn), sv - mn - (s0 - mn)
        M = np.array([[su[0], sv[0]], [su[1], sv[1]]])
        if abs(np.linalg.det(M)) < 1e-9:
            continue
        Mi = np.linalg.inv(M)
        corners = np.array([s0, s0 + su, s0 + sv, s0 + su + sv])
        x0, y0 = np.floor(corners.min(0)).astype(int)
        x1, y1 = np.ceil(corners.max(0)).astype(int)
        x0, y0 = max(x0, 0), max(y0, 0)
        x1, y1 = min(x1, W), min(y1, H)
        if x1 <= x0 or y1 <= y0:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        dx, dy = gx - s0[0], gy - s0[1]
        a = Mi[0, 0] * dx + Mi[0, 1] * dy
        b = Mi[1, 0] * dx + Mi[1, 1] * dy
        m = (a >= 0) & (a < 1) & (b >= 0) & (b < 1)
        if not m.any():
            continue
        z = z0 + a * (zu - z0) + b * (zv - z0)
        th, tw = img.shape[:2]
        u0, v0, u1, v1 = uv
        tu = (u0 + a * (u1 - u0)) * tw / 16.0
        tv = (v0 + b * (v1 - v0)) * th / 16.0
        ti = np.clip(np.floor(tu).astype(int), 0, tw - 1)
        tj = np.clip(np.floor(tv).astype(int), 0, th - 1)
        texel = img[tj, ti]
        m &= texel[..., 3] > 0
        sub_z = zb[y0:y1, x0:x1]
        m &= z > sub_z
        if not m.any():
            continue
        sh = SHADE[face]
        sub_c = col[y0:y1, x0:x1]
        rgb = texel[..., :3] * sh
        sub_c[m, :3] = rgb[m]
        sub_c[m, 3] = 255
        sub_z[m] = z[m]
    out = Image.fromarray(np.clip(col, 0, 255).astype(np.uint8), "RGBA")
    if bg is not None:
        base = Image.new("RGBA", out.size, bg)
        base.alpha_composite(out)
        out = base
    return out


def load_tex(path_or_img):
    im = path_or_img if isinstance(path_or_img, Image.Image) else Image.open(path_or_img)
    return np.asarray(im.convert("RGBA")).astype(np.float32)


def model_quads(model, textures):
    """Quads de um modelo de bloco Java (dict). textures: chave (sem '#') -> numpy RGBA."""
    tx = model.get("textures", {})
    quads = []
    for e in model["elements"]:
        if e.get("rotation") and e["rotation"].get("angle", 0):
            raise ValueError("rotação não suportada: " + e.get("name", "?"))
        for face, d in e["faces"].items():
            key = d["texture"].lstrip("#")
            seen = set()
            while key in tx and tx[key].startswith("#") and key not in seen:
                seen.add(key)
                key = tx[key].lstrip("#")
            img = textures[key]
            p0, U, V = face_quad(face, e["from"], e["to"])
            quads.append((face, p0, U, V, img, d.get("uv", [0, 0, 16, 16])))
    return quads


def side_by_side(images, gap=16, bg=(22, 24, 29, 255)):
    w = sum(i.width for i in images) + gap * (len(images) + 1)
    h = max(i.height for i in images) + 2 * gap
    out = Image.new("RGBA", (w, h), bg)
    x = gap
    for im in images:
        out.alpha_composite(im, (x, gap + (h - 2 * gap - im.height) // 2))
        x += im.width + gap
    return out
