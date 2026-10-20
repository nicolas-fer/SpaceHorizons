"""Gera os modelos de bloco (JSON Java com elements) das máquinas do SpaceHorizons.

Fluxo: este script escreve art/models/src/<nome>.json (fonte geométrica, UV pela posição do
elemento, cullface nas faces que encostam na borda do bloco). Depois cada um é importado
para art/models/<nome>.bbmodel (MCP bbmodel: bbmodel_import_java_block), revisado
(bbmodel_validate / bbmodel_contact_sheet) e exportado para
src/main/resources/assets/spacehorizons/models/block/<nome>.json (bbmodel_export_java_block).

Uso (raiz do repo): python art/models/build_models.py
Coordenadas em pixels de modelo (0..16). Toda a geometria fica dentro do bloco: a forma de
colisão continua cubo cheio, só o visual muda.
"""
import json
from pathlib import Path

NS = "spacehorizons:block/"
OUT = Path(__file__).resolve().parent / "src"
FACES = ("north", "east", "south", "west", "up", "down")


def uv_for(face, f, t):
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
    return [x1, 16 - z2, x2, 16 - z1]  # down


def on_border(face, f, t):
    return {"north": f[2] == 0, "south": t[2] == 16, "west": f[0] == 0, "east": t[0] == 16,
            "down": f[1] == 0, "up": t[1] == 16}[face]


def el(name, f, t, tex, rot=None, skip=(), uv=None):
    """tex: textura de todas as faces (str) ou dict face->textura ('*' = padrão das demais)."""
    if isinstance(tex, str):
        tex = {"*": tex}
    faces = {}
    for face in FACES:
        if face in skip:
            continue
        key = tex.get(face, tex.get("*"))
        if key is None:
            continue
        d = {"uv": (uv or {}).get(face, uv_for(face, f, t)), "texture": "#" + key}
        if rot is None and on_border(face, f, t):
            d["cullface"] = face
        faces[face] = d
    e = {"name": name, "from": list(f), "to": list(t), "faces": faces}
    if rot:
        e["rotation"] = rot
    return e


def model(textures, elements):
    tx = {k: (v if ":" in v else NS + v) for k, v in textures.items()}
    return {"parent": "minecraft:block/block", "textures": tx, "elements": elements}


def refinery():
    tex = {"particle": "refinery_side", "front": "refinery", "side": "refinery_side", "top": "refinery_top",
           "casing": "machine_casing", "copper": "copper_tank", "hazard": "hazard_stripes"}
    return model(tex, [
        el("base", (0, 0, 0), (16, 2, 16), "casing"),
        el("corpo", (1, 2, 1), (15, 12, 15), {"north": "front", "up": "top", "*": "side"}, skip=("down",)),
        el("faixa", (1, 12, 1), (15, 13, 15), {"up": "top", "*": "hazard"}, skip=("down",)),
        el("coluna", (8, 13, 7), (14, 16, 13), {"up": "top", "*": "copper"}, skip=("down",)),
        el("anel_coluna", (7.5, 14, 6.5), (14.5, 15, 13.5), "casing", skip=("up", "down")),
        el("chamine", (2, 13, 9), (5, 16, 12), "casing", skip=("down",)),
        el("tubo_lateral", (15, 2, 9), (16, 11, 11), "copper", skip=("down",)),
        el("tubo_cotovelo", (14, 11, 9), (16, 13, 11), "copper", skip=("down",)),
        el("tubo_topo", (13, 13, 9), (15, 14.5, 11), "copper", skip=("down",)),
        el("pe_frente_esq", (1, 2, 0), (3, 4, 1), "casing", skip=("down", "up")),
        el("pe_frente_dir", (13, 2, 0), (15, 4, 1), "casing", skip=("down", "up")),
    ])


def oxygen_compressor():
    tex = {"particle": "oxygen_compressor_side", "front": "oxygen_compressor", "side": "oxygen_compressor_side",
           "top": "oxygen_compressor_top", "casing": "machine_casing", "cylinder": "oxygen_cylinder"}
    return model(tex, [
        el("base", (0, 0, 0), (16, 1, 16), "casing"),
        el("corpo", (3, 1, 1), (13, 13, 15), {"north": "front", "up": "top", "*": "side"}, skip=("down",)),
        el("topo", (4, 13, 3), (12, 15, 13), {"up": "top", "*": "casing"}, skip=("down",)),
        el("cilindro_esq", (0, 1, 5), (3, 13, 10), "cylinder", skip=("down",)),
        el("cilindro_dir", (13, 1, 5), (16, 13, 10), "cylinder", skip=("down",)),
        el("valvula_esq", (0.5, 13, 6), (2.5, 14.5, 9), "casing", skip=("down",)),
        el("valvula_dir", (13.5, 13, 6), (15.5, 14.5, 9), "casing", skip=("down",)),
        el("tubo_esq", (2.5, 13.5, 7), (4, 14.5, 8), "casing", skip=("east", "west")),
        el("tubo_dir", (12, 13.5, 7), (13.5, 14.5, 8), "casing", skip=("east", "west")),
    ])


def rocket_cockpit():
    tex = {"particle": "rocket_hull", "front": "rocket_cockpit", "side": "rocket_cockpit_side",
           "top": "rocket_cockpit_top", "glass": "cockpit_glass", "hull": "rocket_hull"}
    return model(tex, [
        el("corpo", (0, 0, 1), (16, 11, 16), {"north": "front", "*": "side", "down": "hull"}),
        # moldura saliente da janela (a janela da textura fica 1 px para dentro)
        el("moldura_baixo", (0, 0, 0), (16, 3, 1), {"*": "front"}, skip=("south",)),
        el("moldura_cima", (0, 10, 0), (16, 11, 1), {"*": "front"}, skip=("south",)),
        el("moldura_esq", (13, 3, 0), (16, 10, 1), {"*": "front"}, skip=("south", "up", "down")),
        el("moldura_dir", (0, 3, 0), (3, 10, 1), {"*": "front"}, skip=("south", "up", "down")),
        el("ombro", (1, 11, 1), (15, 14, 15), {"up": "top", "north": "glass", "*": "hull"}, skip=("down",)),
        el("cupula", (3, 14, 3), (13, 16, 13), {"up": "top", "*": "hull"}, skip=("down",)),
    ])


def rocket_engine():
    tex = {"particle": "rocket_engine", "side": "rocket_engine", "top": "rocket_engine_top",
           "bottom": "rocket_engine_bottom", "casing": "machine_casing", "hazard": "hazard_stripes"}
    return model(tex, [
        el("montagem", (0, 13, 0), (16, 16, 16), {"up": "top", "down": "casing", "*": "hazard"}),
        el("camara", (4, 9, 4), (12, 13, 12), "side", skip=("up", "down")),
        el("garganta", (5, 7, 5), (11, 9, 11), "side", skip=("up",)),
        el("sino_alto", (3, 4, 3), (13, 7, 13), {"up": "casing", "*": "side"}, skip=("down",)),
        el("sino_baixo", (1, 0, 1), (15, 4, 15), {"down": "bottom", "up": "casing", "*": "side"}),
        el("haste_no", (1.5, 4, 1.5), (2.5, 13, 2.5), "casing", skip=("up", "down")),
        el("haste_ne", (13.5, 4, 1.5), (14.5, 13, 2.5), "casing", skip=("up", "down")),
        el("haste_so", (1.5, 4, 13.5), (2.5, 13, 14.5), "casing", skip=("up", "down")),
        el("haste_se", (13.5, 4, 13.5), (14.5, 13, 14.5), "casing", skip=("up", "down")),
    ])


def rocket_fuel_tank():
    tex = {"particle": "rocket_fuel_tank", "side": "rocket_fuel_tank", "top": "rocket_fuel_tank_top",
           "casing": "machine_casing"}
    els = [
        el("anel_topo", (0, 14, 0), (16, 16, 16), {"up": "top", "*": "casing"}),
        el("anel_base", (0, 0, 0), (16, 2, 16), {"down": "top", "*": "casing"}),
        el("tanque", (1, 2, 1), (15, 14, 15), "side", skip=("up", "down")),
    ]
    for n, (x, z) in {"no": (0, 0), "ne": (14, 0), "so": (0, 14), "se": (14, 14)}.items():
        els.append(el("coluna_" + n, (x, 2, z), (x + 2, 14, z + 2), "casing", skip=("up", "down")))
    return model(tex, els)


def rocket_parachute():
    tex = {"particle": "rocket_parachute", "side": "rocket_parachute", "top": "rocket_parachute_top",
           "casing": "machine_casing"}
    return model(tex, [
        el("base", (0, 0, 0), (16, 3, 16), "casing"),
        el("pacote", (1, 3, 1), (15, 12, 15), {"up": "top", "*": "side"}, skip=("down",)),
        el("domo", (3, 12, 3), (13, 15, 13), {"up": "top", "*": "side"}, skip=("down",)),
        el("tampa", (6, 15, 6), (10, 16, 10), "casing", skip=("down",)),
        el("cinta_x", (7, 3, 0.5), (9, 12.5, 15.5), "casing", skip=("down",)),
        el("cinta_z", (0.5, 3, 7), (15.5, 12.5, 9), "casing", skip=("down",)),
    ])


def rocket_landing_leg():
    tex = {"particle": "rocket_landing_leg", "leg": "rocket_landing_leg", "foot": "rocket_landing_leg_top",
           "casing": "machine_casing", "hazard": "hazard_stripes"}
    return model(tex, [
        el("montagem", (1, 13, 1), (15, 16, 15), {"up": "casing", "down": "casing", "*": "hazard"}),
        el("cilindro", (5, 5, 5), (11, 13, 11), "leg", skip=("up",)),
        el("pistao", (6.5, 2, 6.5), (9.5, 5, 9.5), "leg", skip=("up", "down")),
        el("sapata", (1, 0, 1), (15, 2, 15), {"up": "foot", "down": "foot", "*": "casing"}),
        el("escora_n", (7, 1.5, 3), (9, 13.5, 4.5), "casing", skip=("up", "down"),
           rot={"angle": 22.5, "axis": "x", "origin": [8, 7.5, 3.75]}),
        el("escora_s", (7, 1.5, 11.5), (9, 13.5, 13), "casing", skip=("up", "down"),
           rot={"angle": -22.5, "axis": "x", "origin": [8, 7.5, 12.25]}),
        el("escora_o", (3, 1.5, 7), (4.5, 13.5, 9), "casing", skip=("up", "down"),
           rot={"angle": -22.5, "axis": "z", "origin": [3.75, 7.5, 8]}),
        el("escora_l", (11.5, 1.5, 7), (13, 13.5, 9), "casing", skip=("up", "down"),
           rot={"angle": 22.5, "axis": "z", "origin": [12.25, 7.5, 8]}),
    ])


def launch_pad():
    tex = {"particle": "launch_pad_side", "top": "launch_pad", "side": "launch_pad_side",
           "casing": "machine_casing", "hazard": "hazard_stripes"}
    els = [
        el("deck", (0, 13, 0), (16, 16, 16), {"up": "top", "down": "casing", "*": "hazard"}),
        el("base", (1, 0, 1), (15, 13, 15), "side", skip=("up",)),
    ]
    for n, (x, z) in {"no": (0, 0), "ne": (13, 0), "so": (0, 13), "se": (13, 13)}.items():
        els.append(el("pilar_" + n, (x, 0, z), (x + 3, 13, z + 3), "casing", skip=("up",)))
    return model(tex, els)


MODELS = {
    "refinery": refinery, "oxygen_compressor": oxygen_compressor, "rocket_cockpit": rocket_cockpit,
    "rocket_engine": rocket_engine, "rocket_fuel_tank": rocket_fuel_tank, "rocket_parachute": rocket_parachute,
    "rocket_landing_leg": rocket_landing_leg, "launch_pad": launch_pad,
}

if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in MODELS.items():
        (OUT / (name + ".json")).write_text(json.dumps(fn(), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(OUT / (name + ".json"))
