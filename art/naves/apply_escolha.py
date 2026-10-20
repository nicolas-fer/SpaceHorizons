"""Aplica a escolha dos blocos da nave: grava texturas e modelos em assets/ com os ids ship_*.

Uso (raiz do repo):
  python art/naves/apply_escolha.py art/_work/escolha_naves/escolhas.json [--dry-run]

Para cada bloco do JSON:
- "texturas": {textura: letra} -> copia art/_work/escolha_naves/var/<textura>/<letra>.png para
  src/main/resources/assets/spacehorizons/textures/block/<textura>.png (vazio = não mexe);
- "modelo": "A"/"B" -> copia art/naves/models/src/<bloco>_<P>.json (gerado por build_ship_models.py) para
  src/main/resources/assets/spacehorizons/models/block/<bloco>.json; "cubo" -> grava cube_all com a textura
  <bloco>; vazio = não mexe.
Blockstates e modelos de item ficam com quem registra os blocos (facing gira a face north = frente).
Os modelos usam também machine_casing e hazard_stripes, que já existem em assets/.
"""
import json
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
VAR = Path("art/_work/escolha_naves/var")
SRC = HERE / "models" / "src"
RES = Path("src/main/resources/assets/spacehorizons")


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry = "--dry-run" in sys.argv
    picks = json.loads(Path(args[0] if args else "art/_work/escolha_naves/escolhas.json").read_text(encoding="utf-8"))
    ops, errors = [], []
    for block, cfg in picks.items():
        if block.startswith("_"):
            continue
        for tex, letter in cfg.get("texturas", {}).items():
            if not letter:
                print(f"{tex}: sem escolha, mantém")
                continue
            src = VAR / tex / f"{letter.upper()}.png"
            if not src.exists():
                errors.append(f"{tex}: variante {letter} não existe ({src})")
                continue
            ops.append((src, RES / "textures/block" / f"{tex}.png", None))
        m = cfg.get("modelo", "")
        dst = RES / "models/block" / f"{block}.json"
        if not m:
            print(f"{block}: modelo sem escolha, mantém")
        elif m == "cubo":
            ops.append((None, dst, {"parent": "minecraft:block/cube_all",
                                    "textures": {"all": f"spacehorizons:block/{block}"}}))
        else:
            src = SRC / f"{block}_{m.upper()}.json"
            if not src.exists():
                errors.append(f"{block}: proposta {m} não existe ({src})")
            else:
                ops.append((src, dst, None))
    if errors:
        raise SystemExit("nada gravado:\n  " + "\n  ".join(errors))
    for src, dst, data in ops:
        print(("[dry-run] " if dry else "") + f"{src or 'cube_all'} -> {dst}")
        if dry:
            continue
        dst.parent.mkdir(parents=True, exist_ok=True)
        if data is not None:
            dst.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
        else:
            shutil.copyfile(src, dst)


if __name__ == "__main__":
    main()
