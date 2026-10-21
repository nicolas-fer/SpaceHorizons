"""Render de um motor multibloco (modelo de art/multiblocos/models/src) com um conjunto de texturas.

Uso (raiz do repo):
  python art/multiblocos/render_engine.py <modelo.json> <saida.png> [nozzle=PNG exit=PNG plate=PNG pump=PNG]
Sem texturas, usa as atuais do mod como provisórias (rocket_engine, rocket_engine_bottom, machine_casing, copper_tank).
Duas vistas lado a lado: de cima (três-quartos) e de baixo (para ver a boca da tubeira).
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import iso  # noqa: E402

TEX = Path("src/main/resources/assets/spacehorizons/textures/block")
DEFAULT = {"nozzle": TEX / "rocket_engine.png", "exit": TEX / "rocket_engine_bottom.png",
           "plate": TEX / "machine_casing.png", "pump": TEX / "copper_tank.png"}


def render_engine(model, textures, scale=None):
    size = max(max(e["to"]) for e in model["elements"]) - min(min(e["from"]) for e in model["elements"])
    scale = scale or (7 if size <= 32 else 5)
    tx = {k: iso.load_tex(v) for k, v in textures.items()}
    tx["particle"] = tx["plate"]
    quads = iso.model_quads(model, tx)
    top = iso.render(quads, iso.View(az=35, el=28, scale=scale))
    bottom = iso.render(quads, iso.View(az=215, el=-24, scale=scale))
    return iso.side_by_side([top, bottom])


def main():
    model = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    tex = dict(DEFAULT)
    for a in sys.argv[3:]:
        k, v = a.split("=", 1)
        tex[k] = Path(v)
    render_engine(model, tex).save(sys.argv[2])


if __name__ == "__main__":
    main()
