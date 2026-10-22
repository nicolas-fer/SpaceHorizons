"""Modelos (elements) do Tier 2: motores nuclear, iônico e de fusão (bloco avulso 1x1, estrutura 2x2 e 3x3, duas
propostas de forma A/B cada) e o gerador radioisotópico (rtg_generator, propostas A/B).

Método (o mesmo de art/multiblocos/build_engines.py, de onde vem a classe Model)
  - Cada forma é desenhada uma vez em coordenadas de projeto 0..32 e escalada para o tamanho:
      1x1: escala 0,5, modelo em 0..16 (com cullface nas faces que encostam na borda do bloco);
      2x2: escala 1, mestre = bloco de menor x/y/z, modelo em 0..32;
      3x3: escala 1,5, mestre = bloco do CENTRO (meio da base, camada do meio), modelo em -16..32.
    Coordenadas arredondadas a 0,5 px. Tudo dentro do limite de modelo de bloco (-16..32), sem rotação.
  - Caixas cortadas nas fronteiras dos blocos; UV = posição dentro do bloco (textura 16x16 1:1 e contínua).
    As faces de "boca" (exit/grid/plasma vistas de baixo) usam a textura inteira esticada sobre a boca.
  - Seções redondas = octógonos de 3 (ou 5) caixas sem sobreposição.

Texturas (chave -> spacehorizons:block/<motor>_<chave>), as mesmas nos três tamanhos de cada motor:
  nuclear_engine  core (blindagem do reator), nozzle (bocal), exit (boca), plate (estrutura e tubos de H2)
  ion_engine      grid (grade de íons, face de baixo), body (carcaça), plate (estrutura), pipe (PPU e linhas de Xe)
  fusion_engine   ring (bobinas magnéticas), body (câmara), plasma (coluna e boca), plate (estrutura)
  particle = spacehorizons:block/<motor> (o apply_escolha.py grava nele a textura principal: core/body/ring).
rtg_generator: side (spacehorizons:block/rtg_generator, aletas e corpo), top (rtg_generator_top), casing
  (machine_casing), hazard (hazard_stripes).

Uso (raiz do repo): python art/tier2/build_models.py
Saída: art/tier2/models/src/<motor>_<1x1|2x2|3x3>_<A|B>.json e rtg_generator_<A|B>.json
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "multiblocos"))
from build_engines import Model  # noqa: E402

OUT = HERE / "models" / "src"
NS = "spacehorizons:block/"
SIZES = {"1x1": (16, 0), "2x2": (32, 0), "3x3": (48, -16)}
KEYS = {"nuclear_engine": ("core", "nozzle", "exit", "plate"),
        "ion_engine": ("grid", "body", "plate", "pipe"),
        "fusion_engine": ("ring", "body", "plasma", "plate")}
MAIN = {"nuclear_engine": "core", "ion_engine": "body", "fusion_engine": "ring"}


def h(v):
    return round(v * 2) / 2


class EModel(Model):
    """Model com escala de projeto (desenho em 0..32) e chaves de textura livres."""

    def __init__(self, engine, size_id):
        S, off = SIZES[size_id]
        super().__init__(S, off)
        self.engine, self.size_id, self.k = engine, size_id, S / 32

    def B(self, name, f, t, tex, skip=(), span=None):
        k = self.k
        self.box(name, [h(v * k) for v in f], [h(v * k) for v in t], tex, skip,
                 tuple(h(v * k) for v in span) if span else None)

    def O(self, name, cx, cz, w, y0, y1, tex, skip=(), mouth=None):
        """Octógono escalado. mouth = chave da face de baixo esticada sobre a boca inteira."""
        k = self.k
        cx, cz, w, y0, y1 = h(cx * k), h(cz * k), max(h(w * k), 1), h(y0 * k), h(y1 * k)
        if isinstance(tex, str):
            tex = {"*": tex}
        span = None
        if mouth:
            tex = dict(tex, down=mouth)
            span = (cx - w / 2, cz - w / 2, cx + w / 2, cz + w / 2)
        if w < 3:
            self.box(name, (cx - w / 2, y0, cz - w / 2), (cx + w / 2, y1, cz + w / 2), tex, skip, span)
        else:
            self.octo(name, cx, cz, w, y0, y1, tex, skip, span)

    def OX(self, name, cy, cz, w, x0, x1, tex):
        k = self.k
        self.octo_x(name, h(cy * k), h(cz * k), max(h(w * k), 2), h(x0 * k), h(x1 * k), tex)

    def bell(self, name, cx, cz, w_top, w_bot, y_top, y_bot, n, wall, mouth, ring=None, rings=(), power=0.6):
        """Bocal em n camadas de y_top a y_bot (projeto), largura w_top -> w_bot com perfil t**power."""
        step = (y_top - y_bot) / n
        for i in range(n):
            t = (i + 1) / n
            w = round(w_top + (w_bot - w_top) * t ** power)
            y1 = y_top - i * step
            y0 = y1 - step
            last = i == n - 1
            self.O(f"{name}_{i:02d}", cx, cz, w, y0, y1, wall, skip=() if last else ("down",),
                   mouth=mouth if last else None)
            if ring and i in rings:
                self.O(f"{name}_anel_{i:02d}", cx, cz, w + 2, y0 + step / 2 - 0.5, y0 + step / 2 + 0.5, ring)

    def json(self, _=None):
        tex = {"particle": NS + self.engine}
        for key in KEYS[self.engine]:
            tex[key] = NS + f"{self.engine}_{key}"
        els = self.elements
        if self.size == 16:
            els = add_cullface(els)
        return {"parent": "minecraft:block/block", "textures": tex, "elements": els}


def add_cullface(elements):
    for e in elements:
        f, t = e["from"], e["to"]
        for face, d in e["faces"].items():
            if {"north": f[2] == 0, "south": t[2] == 16, "west": f[0] == 0, "east": t[0] == 16,
                "down": f[1] == 0, "up": t[1] == 16}[face]:
                d["cullface"] = face
    return elements


# ======================= motor nuclear térmico (NERVA) =======================
def nuclear_A(m):
    """Clássico: placa, escudo, tambor do reator alto, anel inferior, bocal longo e estreito (7 camadas),
    dois tubos de hidrogênio descendo da placa até o topo do bocal e uma turbobomba."""
    m.B("placa", (2, 30, 2), (30, 32, 30), "plate")
    m.O("escudo", 16, 16, 24, 28, 30, "plate")
    m.O("reator", 16, 16, 20, 17, 28, "core")
    m.O("anel_reator", 16, 16, 22, 15.5, 17, "plate")
    m.O("garganta", 16, 16, 7, 13, 15.5, "nozzle")
    m.bell("bocal", 16, 16, 8, 18, 13, 0, 7, "nozzle", "exit")
    for tag, x0, x1, hx0, hx1 in (("o", 3, 5, 5, 12), ("l", 27, 29, 20, 27)):
        m.B("tubo_h2_" + tag, (x0, 13, 15), (x1, 30, 17), "plate")
        m.B("tubo_h2_" + tag + "_2", (hx0, 13, 15), (hx1, 15, 17), "plate")
    m.O("bomba", 6, 6, 5, 19, 28, "plate")
    m.B("tubo_bomba", (8.5, 22, 5), (12, 24, 7), "plate")
    return m


def nuclear_B(m):
    """Reator com tambores de controle: placa grossa com quatro pés, tambor do reator cercado por oito tambores de
    controle e duas cintas, garganta curta e bocal ainda mais longo (13 camadas) com dois anéis de reforço."""
    m.B("placa", (0, 29, 0), (32, 32, 32), "plate")
    m.O("reator", 16, 16, 18, 15, 29, "core")
    for i, (x, z) in enumerate(((16, 4.5), (16, 27.5), (4.5, 16), (27.5, 16), (8, 8), (24, 8), (8, 24), (24, 24))):
        m.B(f"tambor_{i}", (x - 1.5, 16, z - 1.5), (x + 1.5, 29, z + 1.5), "plate")
    m.O("cinta_1", 16, 16, 20, 18, 19, "plate")
    m.O("cinta_2", 16, 16, 20, 25, 26, "plate")
    m.O("base_reator", 16, 16, 14, 13, 15, "plate")
    m.O("garganta", 16, 16, 6, 12, 13, "nozzle")
    m.bell("bocal", 16, 16, 7, 20, 12, 0, 12, "nozzle", "exit", ring="plate", rings=(5, 11), power=0.75)
    return m


# ======================= propulsor iônico =======================
def ion_A(m):
    """Propulsor único, raso e largo: placa, duas caixas de eletrônica (PPU), dois tanques de xenônio, carcaça
    larga e baixa, flange e a grade de íons ocupando a boca inteira, catodo neutralizador na lateral."""
    m.B("placa", (1, 29, 1), (31, 32, 31), "plate")
    m.B("ppu_1", (2, 24, 2), (10, 29, 9), "pipe")
    m.B("ppu_2", (22, 24, 23), (30, 29, 30), "pipe")
    m.O("xenonio_1", 26, 6, 6, 23, 29, "pipe")
    m.O("xenonio_2", 6, 26, 6, 23, 29, "pipe")
    m.O("cupula", 16, 16, 18, 22, 29, "body")
    m.O("carcaca", 16, 16, 28, 12, 22, "body")
    m.O("flange", 16, 16, 30, 10, 12, "plate")
    m.O("grade", 16, 16, 26, 9, 10, "plate", mouth="grid")
    m.B("catodo", (29.5, 9, 15), (31.5, 14, 17), "pipe")
    m.B("linha_xe", (24, 22, 15), (26, 24, 26), "pipe")
    return m


def ion_B(m):
    """Cacho de quatro propulsores: placa grossa, PPU central com linhas de xenônio para cada propulsor, e quatro
    carcaças com flange e grade própria (uma boca por propulsor)."""
    m.B("placa", (0, 28, 0), (32, 32, 32), "plate")
    m.B("ppu", (12, 19, 12), (20, 28, 20), "pipe")
    for i, (cx, cz) in enumerate(((8, 8), (24, 8), (8, 24), (24, 24))):
        m.O(f"carcaca_{i}", cx, cz, 13, 16, 28, "body")
        m.O(f"flange_{i}", cx, cz, 14, 14, 16, "plate")
        m.O(f"grade_{i}", cx, cz, 12, 13, 14, "plate", mouth="grid")
    m.B("linha_x", (6, 22, 15), (26, 24, 17), "pipe")
    m.B("linha_z", (15, 22, 6), (17, 24, 26), "pipe")
    return m


# ======================= motor de fusão =======================
def fusion_A(m):
    """Bocal magnético: placa, câmara de contenção com o anel do toro em volta, garganta, coluna de plasma visível
    entre três bobinas que se abrem para baixo, quatro hastes ligando as bobinas à câmara."""
    m.B("placa", (1, 29, 1), (31, 32, 31), "plate")
    m.O("camara", 16, 16, 20, 19, 29, "body")
    m.O("toro", 16, 16, 27, 21, 26, "ring")
    m.O("garganta", 16, 16, 9, 16, 19, "body")
    m.O("plasma", 16, 16, 6, 0, 16, "plasma", mouth="plasma")
    for i, (w, y) in enumerate(((13, 13), (17, 8), (22, 3))):
        m.O(f"bobina_{i}", 16, 16, w, y, y + 2, "ring")
    for i, (x, z) in enumerate(((16, 4.5), (16, 27.5), (4.5, 16), (27.5, 16))):
        m.B(f"haste_{i}", (x - 0.75, 3, z - 0.75), (x + 0.75, 21, z + 0.75), "plate")
    return m


def fusion_B(m):
    """Toro grande: placa, toro quadrado largo em volta da câmara, quatro grampos, coluna de plasma mais grossa
    entre três bobinas largas e quatro hastes."""
    m.B("placa", (0, 29, 0), (32, 32, 32), "plate")
    m.O("camara", 16, 16, 12, 17, 29, "body")
    m.B("toro_n", (2, 20, 2), (30, 27, 8), "ring")
    m.B("toro_s", (2, 20, 24), (30, 27, 30), "ring")
    m.B("toro_o", (2, 20, 8), (8, 27, 24), "ring")
    m.B("toro_l", (24, 20, 8), (30, 27, 24), "ring")
    for i, (x, z) in enumerate(((1, 1), (27, 1), (1, 27), (27, 27))):
        m.B(f"grampo_{i}", (x, 19, z), (x + 4, 29, z + 4), "plate")
    for i, (a, b, c, d) in enumerate(((8, 15, 10, 17), (22, 15, 24, 17), (15, 8, 17, 10), (15, 22, 17, 24))):
        m.B(f"braco_{i}", (a, 23, b), (c, 25, d), "plate")
    m.O("plasma", 16, 16, 8, 0, 17, "plasma", mouth="plasma")
    for i, (w, y) in enumerate(((15, 13), (19, 8), (24, 3))):
        m.O(f"bobina_{i}", 16, 16, w, y, y + 2.5, "ring")
    for i, (x, z) in enumerate(((16, 3), (16, 29), (3, 16), (29, 16))):
        m.B(f"haste_{i}", (x - 0.75, 3, z - 0.75), (x + 0.75, 20, z + 0.75), "plate")
    return m


DESIGNS = {"nuclear_engine": {"A": nuclear_A, "B": nuclear_B},
           "ion_engine": {"A": ion_A, "B": ion_B},
           "fusion_engine": {"A": fusion_A, "B": fusion_B}}


# ======================= gerador radioisotópico (1 bloco) =======================
class RModel(Model):
    def json(self, _=None):
        return {"parent": "minecraft:block/block",
                "textures": {"particle": NS + "rtg_generator", "side": NS + "rtg_generator",
                             "top": NS + "rtg_generator_top", "casing": NS + "machine_casing",
                             "hazard": NS + "hazard_stripes"},
                "elements": add_cullface(self.elements)}


def rtg_A():
    """Cilindro com aletas em cruz: base, corpo octogonal, faixa de alerta, oito aletas radiais (pares paralelos
    em cruz) e tampa com o topo radioativo."""
    m = RModel(16, 0)
    m.box("base", (1, 0, 1), (15, 2, 15), "casing")
    m.octo("corpo", 8, 8, 8, 2, 14, "side")
    m.octo("faixa", 8, 8, 9, 3, 4, "hazard")
    for i, (f, t) in enumerate((((6, 2, 0), (7, 13, 4)), ((9, 2, 0), (10, 13, 4)), ((6, 2, 12), (7, 13, 16)),
                                ((9, 2, 12), (10, 13, 16)), ((0, 2, 6), (4, 13, 7)), ((0, 2, 9), (4, 13, 10)),
                                ((12, 2, 6), (16, 13, 7)), ((12, 2, 9), (16, 13, 10)))):
        m.box(f"aleta_{i}", f, t, "side")
    m.octo("tampa", 8, 8, 10, 14, 16, {"*": "casing", "up": "top"})
    return m


def rtg_B():
    """Caixa com aletas em pente: base e tampa de carcaça, corpo quadrado, três aletas por lado (doze no total),
    faixa de alerta e o topo radioativo."""
    m = RModel(16, 0)
    m.box("base", (1, 0, 1), (15, 2, 15), "casing")
    m.box("corpo", (4, 2, 4), (12, 14, 12), "side")
    m.box("faixa", (3.5, 12, 3.5), (12.5, 13, 12.5), "hazard")
    for i, z in enumerate((4.5, 7.5, 10.5)):
        m.box(f"aleta_l_{i}", (12, 3, z), (15.5, 12, z + 1), "side")
        m.box(f"aleta_o_{i}", (0.5, 3, z), (4, 12, z + 1), "side")
        m.box(f"aleta_s_{i}", (z, 3, 12), (z + 1, 12, 15.5), "side")
        m.box(f"aleta_n_{i}", (z, 3, 0.5), (z + 1, 12, 4), "side")
    m.box("tampa", (3, 14, 3), (13, 15, 13), "casing")
    m.box("topo", (5, 15, 5), (11, 16, 11), {"*": "casing", "up": "top"})
    return m


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    OUT.mkdir(parents=True, exist_ok=True)
    for engine, props in DESIGNS.items():
        for size in SIZES:
            for prop, fn in props.items():
                data = fn(EModel(engine, size)).json()
                lo = min(min(e["from"]) for e in data["elements"])
                hi = max(max(e["to"]) for e in data["elements"])
                assert -16 <= lo and hi <= 32, (engine, size, prop, lo, hi)
                p = OUT / f"{engine}_{size}_{prop}.json"
                p.write_text(json.dumps(data, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
                print(p.name, len(data["elements"]), "elements, limites", lo, hi)
    for prop, fn in (("A", rtg_A), ("B", rtg_B)):
        data = fn().json()
        p = OUT / f"rtg_generator_{prop}.json"
        p.write_text(json.dumps(data, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
        print(p.name, len(data["elements"]), "elements")


if __name__ == "__main__":
    main()
