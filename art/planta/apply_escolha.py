"""Aplica a escolha da planta de foguete (art/_work/escolha_planta/escolhas.json) nos assets do mod.

Uso: python art/planta/apply_escolha.py [pasta_da_escolha]
Copia art/planta/var/<item>/<valor>.png para textures/item/<item>.png. "atual" (ou vazio) não muda nada.
"""
import json
import shutil
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent.parent
PASTA = Path(sys.argv[1]) if len(sys.argv) > 1 else RAIZ / "art/_work/escolha_planta"
DEST = RAIZ / "src/main/resources/assets/spacehorizons/textures/item"
ITENS = ["rocket_blueprint", "rocket_blueprint_blank"]

escolhas = json.loads((PASTA / "escolhas.json").read_text(encoding="utf-8")).get("escolhas", {})
for item in ITENS:
    valor = escolhas.get(item, {}).get(f"tex.{item}", "")
    if not valor or valor == "atual":
        print(f"{item}: mantida a atual")
        continue
    shutil.copy(RAIZ / "art/planta/var" / item / f"{valor}.png", DEST / f"{item}.png")
    print(f"{item}: gravada a variante {valor}")
