"""Modelos (elements) dos motores multibloco formados: 2x2 (base 2x2, altura 2) e 3x3 (base 3x3, altura 3),
duas propostas de forma (A e B) para cada tamanho.

Método
  - Geometria escrita em coordenadas locais da estrutura (0..32 no 2x2, 0..48 no 3x3, em pixels de modelo) e
    deslocada para o bloco mestre que desenha o conjunto:
      2x2: mestre = bloco de menor x, y e z da estrutura; o modelo ocupa 0..32 nos três eixos.
      3x3: mestre = bloco do CENTRO da estrutura (meio da base 3x3, camada do meio); o modelo ocupa -16..32.
    Assim tudo fica dentro do limite de modelo de bloco do Minecraft (-16..32), sem dividir em partes.
  - Cada caixa é cortada nas linhas da grade de 16 (fronteiras dos blocos) e o UV de cada pedaço é a posição dele
    dentro do seu bloco (como art/models/build_models.py): a textura 16x16 fica 1:1 (1 pixel de modelo = 1 texel)
    e contínua entre os pedaços. Faces internas criadas pelo corte são omitidas.
  - Seções redondas (câmara, garganta, sino, turbobombas) são octógonos de 3 caixas sem sobreposição (sem faces
    coplanares brigando). Nada usa rotação.
  - Sem cullface: os outros blocos da estrutura ficam invisíveis enquanto formada.

Texturas (chaves do modelo -> ids): nozzle (sino e câmara), exit (boca da tubeira, vista de baixo),
plate (placa de montagem e estrutura), pump (turbobombas e tubulação). Ids:
  spacehorizons:block/rocket_engine_<2x2|3x3>_<nozzle|exit|plate|pump>

Uso (raiz do repo): python art/multiblocos/build_engines.py
Saída: art/multiblocos/models/src/rocket_engine_<tam>_<A|B>.json
"""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "models" / "src"
FACES = ("north", "east", "south", "west", "up", "down")


def uv_local(face, f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    if face == "north":
        return [16 - x2, 16 - y2, 16 - x1, 16 - y1]
    if face == "south":
        return [x1, 16 - y2, x2, 16 - y1]
    if face == "east":
        return [16 - z2, 16 - y2, 16 - z1, 16 - y1]
    if face == "west":
        return [z1, 16 - y2, z2, 16 - y1]
    if face == "up":
        return [x1, z1, x2, z2]
    return [x1, 16 - z2, x2, 16 - z1]


def cuts(a, b):
    """Intervalos de [a, b] cortados nas múltiplas de 16."""
    out, s = [], a
    k = (int(a // 16) + 1) * 16
    while k < b:
        out.append((s, k))
        s = k
        k += 16
    out.append((s, b))
    return out


def r(v):
    v = round(v * 2) / 2
    return int(v) if v == int(v) else v


class Model:
    def __init__(self, size, offset):
        self.size, self.offset = size, offset
        self.elements = []

    def box(self, name, f, t, tex, skip=(), span=None):
        """f, t em coordenadas locais. tex: str ou dict face->chave ('*' = demais).
        span=(x0, z0, x1, z1): a face down usa a textura inteira esticada sobre esse retângulo (boca da tubeira:
        um brilho só no meio, em vez de um por bloco)."""
        if isinstance(tex, str):
            tex = {"*": tex}
        f = [r(v) for v in f]
        t = [r(v) for v in t]
        xs, ys, zs = cuts(f[0], t[0]), cuts(f[1], t[1]), cuts(f[2], t[2])
        n = 0
        for xa, xb in xs:
            for ya, yb in ys:
                for za, zb in zs:
                    if xb <= xa or yb <= ya or zb <= za:
                        continue
                    cell = (int(xa // 16), int(ya // 16), int(za // 16))
                    lf = (xa - cell[0] * 16, ya - cell[1] * 16, za - cell[2] * 16)
                    lt = (xb - cell[0] * 16, yb - cell[1] * 16, zb - cell[2] * 16)
                    faces = {}
                    for face in FACES:
                        if face in skip:
                            continue
                        inner = {"west": xa != f[0], "east": xb != t[0], "down": ya != f[1], "up": yb != t[1],
                                 "north": za != f[2], "south": zb != t[2]}[face]
                        if inner:
                            continue
                        key = tex.get(face, tex.get("*"))
                        if key is None:
                            continue
                        if face == "down" and span:
                            sx0, sz0, sx1, sz1 = span
                            k = 16 / (sx1 - sx0)
                            uv = [round((xa - sx0) * k, 3), round((sz1 - zb) * k, 3),
                                  round((xb - sx0) * k, 3), round((sz1 - za) * k, 3)]
                        else:
                            uv = [r(v) for v in uv_local(face, lf, lt)]
                        faces[face] = {"uv": uv, "texture": "#" + key}
                    if not faces:
                        continue
                    o = self.offset
                    n += 1
                    label = name if len(xs) * len(ys) * len(zs) == 1 else f"{name}_{n}"
                    self.elements.append({"name": label, "from": [r(xa + o), r(ya + o), r(za + o)],
                                          "to": [r(xb + o), r(yb + o), r(zb + o)], "faces": faces})

    def octo(self, name, cx, cz, w, y0, y1, tex, skip=(), span=None):
        """Prisma "redondo" de largura w (centro cx, cz) entre y0 e y1, em faixas de z sem sobreposição:
        3 faixas (octógono) até w < 18, 5 faixas a partir daí (mais redondo)."""
        R = w / 2
        if w < 18:
            c = round(w * 0.29 * 2) / 2
            strips = [(0, R - c, R), (R - c, R, R - c)]
        else:
            strips = [(0, 0.38 * R, R), (0.38 * R, 0.78 * R, 0.81 * R), (0.78 * R, R, 0.46 * R)]
        for i, (z0, z1, hw) in enumerate(strips):
            if i == 0:
                self.box(f"{name}_f0", (cx - hw, y0, cz - z1), (cx + hw, y1, cz + z1), tex, skip, span)
                continue
            self.box(f"{name}_f{i}n", (cx - hw, y0, cz - z1), (cx + hw, y1, cz - z0), tex, skip, span)
            self.box(f"{name}_f{i}s", (cx - hw, y0, cz + z0), (cx + hw, y1, cz + z1), tex, skip, span)

    def octo_x(self, name, cy, cz, w, x0, x1, tex):
        """Prisma octogonal deitado ao longo de x (turbobomba horizontal)."""
        c = round(w * 0.29 * 2) / 2
        h = w / 2
        self.box(name + "_meio", (x0, cy - h + c, cz - h), (x1, cy + h - c, cz + h), tex)
        self.box(name + "_baixo", (x0, cy - h, cz - h + c), (x1, cy - h + c, cz + h - c), tex)
        self.box(name + "_cima", (x0, cy + h - c, cz - h + c), (x1, cy + h, cz + h - c), tex)

    def bell(self, name, cx, cz, widths, y_top, step=2, rings=()):
        """Sino da tubeira: camadas de 'step' de altura, de cima (widths[0]) para baixo. A camada de baixo
        tem a boca (face down) com a textura 'exit'. rings: índices de camadas com anel de reforço (pump)."""
        n = len(widths)
        for i, w in enumerate(widths):
            y1 = y_top - i * step
            y0 = y1 - step
            last = i == n - 1
            tex = {"*": "nozzle", "down": "exit"} if last else "nozzle"
            span = (cx - w / 2, cz - w / 2, cx + w / 2, cz + w / 2) if last else None
            self.octo(f"{name}_{i:02d}", cx, cz, w, y0, y1, tex, skip=() if last else ("down",), span=span)
            if i in rings:
                self.octo(f"{name}_anel_{i:02d}", cx, cz, w + 2, y0 + step / 2 - 0.5, y0 + step / 2 + 0.5, "pump")
        return y_top - n * step

    def json(self, size_id):
        ns = "spacehorizons:block/rocket_engine_" + size_id + "_"
        return {"parent": "minecraft:block/block",
                "textures": {"particle": ns + "plate", "nozzle": ns + "nozzle", "exit": ns + "exit",
                             "plate": ns + "plate", "pump": ns + "pump"},
                "elements": self.elements}


# ---------------- 2x2 (local 0..32, mestre no canto: offset 0) ----------------
def e2_A():
    """Clássico: placa cheia, domo do injetor, câmara, garganta, sino largo de 8 camadas e duas turbobombas
    verticais em cantos opostos com tubos até a câmara e escape de turbina descendo ao lado do sino."""
    m = Model(32, 0)
    m.box("placa", (1, 28, 1), (31, 32, 31), "plate")
    m.octo("domo", 16, 16, 14, 25, 28, "plate")
    m.octo("camara", 16, 16, 10, 19, 25, "nozzle")
    m.octo("garganta", 16, 16, 6, 17, 19, "nozzle")
    m.bell("sino", 16, 16, [8, 11, 14, 17, 20, 22, 25, 28], 17)
    for tag, cx, cz, dx in (("a", 6, 6, 1), ("b", 26, 26, -1)):
        m.octo("bomba_" + tag, cx, cz, 7, 18, 28, "pump")
        # tubo em L: da bomba até o eixo da câmara e dali até a parede dela
        x0, x1 = (cx + 3.5, 17) if dx > 0 else (15, cx - 3.5)
        m.box("tubo_" + tag, (x0, 21, cz - 1), (x1, 23, cz + 1), "pump")
        z0, z1 = (cz + 1, 11) if dx > 0 else (21, cz - 1)
        m.box("tubo_" + tag + "_2", (15, 21, z0), (17, 23, z1), "pump")
        m.box("escape_" + tag, (cx - 1, 6, cz - 1), (cx + 1, 18, cz + 1), "plate")
    m.box("linha_lox", (5, 23, 21), (7, 28, 23), "pump")
    m.box("linha_comb", (25, 23, 9), (27, 28, 11), "pump")
    return m


def e2_B():
    """Treliça: estrutura de empuxo em cruz com sapatas de canto, pernas e quadro em volta da câmara; câmara
    alta com anel coletor, sino mais longo de 9 camadas com um anel de reforço e uma turbobomba deitada com o
    escape descendo pela lateral do sino."""
    m = Model(32, 0)
    m.box("quadro_topo_n", (0, 29, 0), (32, 32, 4), "plate")
    m.box("quadro_topo_s", (0, 29, 28), (32, 32, 32), "plate")
    m.box("quadro_topo_o", (0, 29, 4), (4, 32, 28), "plate")
    m.box("quadro_topo_l", (28, 29, 4), (32, 32, 28), "plate")
    m.box("viga_x_o", (4, 29, 13), (13, 32, 19), "plate")
    m.box("viga_x_l", (19, 29, 13), (28, 32, 19), "plate")
    m.box("viga_z_n", (13, 29, 4), (19, 32, 13), "plate")
    m.box("viga_z_s", (13, 29, 19), (19, 32, 28), "plate")
    m.box("cubo_central", (13, 29, 13), (19, 32, 19), "plate")
    for i, (x, z) in enumerate(((1, 1), (27, 1), (1, 27), (27, 27))):
        m.box(f"perna_{i}", (x + 1, 24, z + 1), (x + 3, 29, z + 3), "plate")
    m.box("quadro_n", (2, 22, 2), (30, 24, 4), "plate")
    m.box("quadro_s", (2, 22, 28), (30, 24, 30), "plate")
    m.box("quadro_o", (2, 22, 4), (4, 24, 28), "plate")
    m.box("quadro_l", (28, 22, 4), (30, 24, 28), "plate")
    for i, (a, b, c_, d) in enumerate(((4, 15, 11, 17), (21, 15, 28, 17), (15, 4, 17, 11), (15, 21, 17, 28))):
        m.box(f"braco_{i}", (a, 22, b), (c_, 24, d), "plate")
    m.octo("camara", 16, 16, 10, 20, 29, "nozzle")
    m.octo("coletor", 16, 16, 12, 20, 21.5, "pump")
    m.octo("garganta", 16, 16, 6, 18, 20, "nozzle")
    m.bell("sino", 16, 16, [8, 11, 14, 16, 18, 20, 22, 23, 24], 18, rings=(5,))
    m.octo_x("bomba", 25, 12, 6, 20, 27, "pump")
    m.box("tubo_bomba", (18, 24, 11), (20, 26, 13), "pump")
    m.box("escape", (24, 3, 11), (26, 22, 13), "plate")
    m.box("linha", (12, 24, 21), (14, 29, 23), "pump")
    return m


# ---------------- 3x3 (local 0..48, mestre no centro: offset -16) ----------------
def e3_A():
    """Clássico grande: placa cheia, domo, câmara, garganta, sino de 13 camadas com anel na boca, duas
    turbobombas verticais em cantos opostos com tubos e escapes, e duas linhas de propelente."""
    m = Model(48, -16)
    m.box("placa", (1, 43, 1), (47, 48, 47), "plate")
    m.octo("domo", 24, 24, 20, 39, 43, "plate")
    m.octo("camara", 24, 24, 14, 30, 39, "nozzle")
    m.octo("garganta", 24, 24, 9, 27, 30, "nozzle")
    m.bell("sino", 24, 24, [12, 16, 20, 23, 26, 29, 31, 34, 36, 38, 40, 42, 44], 27, rings=(12,))
    for tag, cx, cz, dx in (("a", 8, 8, 1), ("b", 40, 40, -1)):
        m.octo("bomba_" + tag, cx, cz, 9, 28, 43, "pump")
        x0, x1 = (cx + 4.5, 25) if dx > 0 else (23, cx - 4.5)
        m.box("tubo_" + tag, (x0, 33, cz - 1), (x1, 35, cz + 1), "pump")
        z0, z1 = (cz + 1, 17) if dx > 0 else (31, cz - 1)
        m.box("tubo_" + tag + "_2", (23, 33, z0), (25, 35, z1), "pump")
        m.box("escape_" + tag, (cx - 1.5, 8, cz - 1.5), (cx + 1.5, 28, cz + 1.5), "plate")
    m.box("linha_lox", (7, 36, 32), (10, 43, 35), "pump")
    m.box("linha_comb", (38, 36, 13), (41, 43, 16), "pump")
    return m


def e3_B():
    """Estrutura cônica: placa em anel escalonado (três degraus) que afunila até o domo, quatro pernas, câmara
    com anel coletor, sino longo de 13 camadas com dois anéis de reforço, quatro turbobombas pequenas deitadas
    (uma por lado) e escape único descendo pela frente."""
    m = Model(48, -16)
    m.box("placa", (0, 45, 0), (48, 48, 48), "plate")
    m.box("degrau_1", (6, 42, 6), (42, 45, 42), "plate")
    m.box("degrau_2", (12, 39, 12), (36, 42, 36), "plate")
    for i, (x, z) in enumerate(((3, 3), (42, 3), (3, 42), (42, 42))):
        m.box(f"perna_{i}", (x, 34, z), (x + 3, 45, z + 3), "plate")
    m.box("quadro_n", (3, 32, 3), (45, 34, 6), "plate")
    m.box("quadro_s", (3, 32, 42), (45, 34, 45), "plate")
    m.box("quadro_o", (3, 32, 6), (6, 34, 42), "plate")
    m.box("quadro_l", (42, 32, 6), (45, 34, 42), "plate")
    for i, (a, b, c_, d) in enumerate(((6, 23, 15.5, 25), (32.5, 23, 42, 25), (23, 6, 25, 15.5), (23, 32.5, 25, 42))):
        m.box(f"braco_{i}", (a, 32, b), (c_, 34, d), "plate")
    m.octo("camara", 24, 24, 15, 30, 39, "nozzle")
    m.octo("coletor", 24, 24, 17, 30, 32, "pump")
    m.octo("garganta", 24, 24, 9, 27, 30, "nozzle")
    m.bell("sino", 24, 24, [12, 16, 19, 22, 25, 28, 31, 34, 36, 38, 40, 42, 44], 27, rings=(4, 10))
    m.octo_x("bomba_n", 36, 9, 6, 17, 31, "pump")
    m.octo_x("bomba_s", 36, 39, 6, 17, 31, "pump")
    m.box("tubo_n", (22, 33, 12), (26, 35, 16.5), "pump")
    m.box("tubo_s", (22, 33, 31.5), (26, 35, 36), "pump")
    m.box("escape", (30, 6, 8), (33, 33, 11), "plate")
    return m


MODELS = {("2x2", "A"): e2_A, ("2x2", "B"): e2_B, ("3x3", "A"): e3_A, ("3x3", "B"): e3_B}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for (size, prop), fn in MODELS.items():
        m = fn()
        data = m.json(size)
        lo = min(min(e["from"]) for e in data["elements"])
        hi = max(max(e["to"]) for e in data["elements"])
        assert -16 <= lo and hi <= 32, (size, prop, lo, hi)
        p = OUT / f"rocket_engine_{size}_{prop}.json"
        p.write_text(json.dumps(data, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
        print(p.name, len(data["elements"]), "elements, limites", lo, hi)


if __name__ == "__main__":
    main()
