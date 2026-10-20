#!/usr/bin/env python3
"""
Gera os modelos PROVISORIOS dos motores multibloco (docs/DESIGN_FOGUETE.md, "Multiblocos") e o blockstate do motor, sem desenho novo: os
elements do motor atual (models/block/rocket_engine.json) ESCALADOS para cobrir o conjunto.

  rocket_engine_2x2   motor 2x2x2: o modelo atual ampliado 2x. O bloco MESTRE e o canto minimo da estrutura, e o modelo se estende
                      para +x, +y e +z a partir dele (0 a 32). Ao girar a estrutura no pouso, o blockstate gira o modelo junto com FACING.
  rocket_engine_3x3   motor 3x3x3: ampliado 3x e DESLOCADO de -16 em cada eixo, para ficar entre -16 e 32 (o Minecraft so aceita elements
                      nessa faixa); o MESTRE e o bloco CENTRAL da estrutura, entao o modelo cobre os 27 blocos e e simetrico no giro.
  rocket_engine_part  vazio: os outros blocos de um motor formado ficam invisiveis (so a particula).

O que e provisorio: a ampliacao estica os pixels e as texturas (o UV nao muda). A arte final dos motores grandes (Blockbench, feita a parte
em art/_work/) entra trocando os dois JSON de modelo, mantendo estes ids (spacehorizons:block/rocket_engine_2x2 e _3x3), sem mudar codigo.
As faces perdem o "cullface" (o modelo ultrapassa os limites do bloco).

Uso:
  python tools/gen_engine_structures.py

Regera tambem blockstates/rocket_engine.json (form = solo | master_2 | master_3 | part; facing so gira o master_2).
"""
import copy
import json
import os

ASSETS = os.path.join("src", "main", "resources", "assets", "spacehorizons")
NAMESPACE = "spacehorizons"


def scaled(model, factor, shift):
    out = copy.deepcopy(model)
    out["credit"] = "Provisorio: gerado por tools/gen_engine_structures.py a partir de rocket_engine.json (elements ampliados)"
    for element in out["elements"]:
        element["from"] = [round(c * factor + shift, 4) for c in element["from"]]
        element["to"] = [round(c * factor + shift, 4) for c in element["to"]]
        for face in element["faces"].values():
            face.pop("cullface", None)
        element["name"] = element.get("name", "e") + "_x" + str(factor)
    for corner in ("from", "to"):
        for element in out["elements"]:
            for c in element[corner]:
                if not -16 <= c <= 32:
                    raise SystemExit("element fora de -16..32: " + str(element))
    return out


def write(path, data):
    with open(path, "w", newline="\n") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")


def main():
    models = os.path.join(ASSETS, "models", "block")
    with open(os.path.join(models, "rocket_engine.json"), encoding="utf8") as fh:
        base = json.load(fh)
    write(os.path.join(models, "rocket_engine_2x2.json"), scaled(base, 2, 0))
    write(os.path.join(models, "rocket_engine_3x3.json"), scaled(base, 3, -16))
    write(os.path.join(models, "rocket_engine_part.json"),
          {"textures": {"particle": base["textures"]["particle"]}, "elements": []})

    variants = {}
    facings = ["north", "east", "south", "west"]
    for i, facing in enumerate(facings):
        variants[f"form=solo,facing={facing}"] = {"model": f"{NAMESPACE}:block/rocket_engine"}
        variants[f"form=part,facing={facing}"] = {"model": f"{NAMESPACE}:block/rocket_engine_part"}
        variants[f"form=master_3,facing={facing}"] = {"model": f"{NAMESPACE}:block/rocket_engine_3x3"}
        entry = {"model": f"{NAMESPACE}:block/rocket_engine_2x2"}
        if i:
            entry["y"] = 90 * i
        variants[f"form=master_2,facing={facing}"] = entry
    write(os.path.join(ASSETS, "blockstates", "rocket_engine.json"), {"variants": variants})
    print("modelos rocket_engine_2x2, rocket_engine_3x3, rocket_engine_part e blockstates/rocket_engine.json gerados")


if __name__ == "__main__":
    main()
