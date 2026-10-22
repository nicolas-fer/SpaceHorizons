"""Render isométrico de um modelo do Tier 2 (art/tier2/models/src) com um conjunto de texturas.
Usa o renderizador de art/multiblocos/iso.py (z-buffer, amostragem por vizinho mais próximo).

Uso (raiz do repo):
  python art/tier2/render.py <modelo.json> <saida.png> [chave=PNG ...]
Sem textura para uma chave, usa uma provisória do mod (PLACEHOLDER).
Duas vistas lado a lado: de cima (três-quartos) e de baixo (boca do motor).
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "multiblocos"))
import iso  # noqa: E402

TEX = Path("src/main/resources/assets/spacehorizons/textures/block")
PLACEHOLDER = {
    "core": TEX / "machine_casing.png", "nozzle": TEX / "rocket_engine_2x2_nozzle.png",
    "exit": TEX / "rocket_engine_2x2_exit.png", "plate": TEX / "rocket_engine_2x2_plate.png",
    "grid": TEX / "rocket_engine_2x2_exit.png", "body": TEX / "copper_tank.png", "pipe": TEX / "rocket_engine_2x2_pump.png",
    "ring": TEX / "copper_tank.png", "plasma": TEX / "rocket_engine_2x2_exit.png",
    "side": TEX / "machine_casing.png", "top": TEX / "rocket_engine_top.png",
    "casing": TEX / "machine_casing.png", "hazard": TEX / "hazard_stripes.png",
}


def render_model(model, textures, scale=None, bottom=True):
    els = model["elements"]
    size = max(max(e["to"]) for e in els) - min(min(e["from"]) for e in els)
    scale = scale or (12 if size <= 16 else 7 if size <= 32 else 5)
    keys = {d["texture"].lstrip("#") for e in els for d in e["faces"].values()}
    tx = {k: iso.load_tex(textures.get(k, PLACEHOLDER.get(k))) for k in keys}
    quads = iso.model_quads(model, tx)
    views = [iso.render(quads, iso.View(az=35, el=28, scale=scale))]
    if bottom:
        views.append(iso.render(quads, iso.View(az=215, el=-24, scale=scale)))
    return iso.side_by_side(views)


def main():
    model = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    tex = {}
    for a in sys.argv[3:]:
        k, v = a.split("=", 1)
        tex[k] = Path(v)
    render_model(model, tex).save(sys.argv[2])


if __name__ == "__main__":
    main()
