"""Aplica a escolha dos multiblocos (escolhas.json do escolha.py) em assets/, com os ids que o código usa.

Uso (raiz do repo):
  python art/multiblocos/apply_escolha.py [art/_work/escolha_multiblocos/escolhas.json] [--dry-run]

Ids gravados (src/main/resources/assets/spacehorizons/):
  tanque     textures/block/rocket_fuel_tank_side_ct.png   lado (base do gen_connected_block.py)
             textures/block/rocket_fuel_tank_end.png       topo e fundo (base do gen_connected_block.py)
  motor 2x2  models/block/rocket_engine_2x2.json           estrutura formada; mestre = canto de menor x/y/z, modelo em 0..32
             textures/block/rocket_engine_2x2_{nozzle,exit,plate,pump}.png
  motor 3x3  models/block/rocket_engine_3x3.json           estrutura formada; mestre = bloco do centro, modelo em -16..32
             textures/block/rocket_engine_3x3_{nozzle,exit,plate,pump}.png
Os modelos já apontam para spacehorizons:block/rocket_engine_<tam>_<textura> (build_engines.py).

Tanque: depois de aplicar, gere as variantes conectadas com o --frame que o script imprime (4 para as variantes;
2 só se lado e topo forem "atual"):
  python tools/gen_connected_block.py --name <id do bloco> --texture spacehorizons:block/rocket_fuel_tank_side_ct \
      --texture-end spacehorizons:block/rocket_fuel_tank_end --frame 4
Se só um dos dois for "atual", ele é convertido para o formato de moldura 4 (tank_ct.to_ct).
Topo: vale "tex.topo"; em branco, vale a opção da pergunta "coluna". Pergunta em branco = não mexe (avisa).
Lê as texturas de art/_work/escolha_multiblocos/var/ (geradas por build_escolha.py).
"""
import json
import shutil
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import tank_ct  # noqa: E402

VAR = Path("art/_work/escolha_multiblocos/var")
SRC = HERE / "models" / "src"
RES = Path("src/main/resources/assets/spacehorizons")
ENGINE_KEYS = ("nozzle", "exit", "plate", "pump")


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry = "--dry-run" in sys.argv
    path = Path(args[0] if args else "art/_work/escolha_multiblocos/escolhas.json")
    esc = json.loads(path.read_text(encoding="utf-8"))["escolhas"]
    ops, errors, notes = [], [], []   # ops: (src Path | Image | None, dst Path)

    # ---- tanque ----
    t = esc.get("tanque", {})
    side, end = t.get("tex.lado", ""), t.get("tex.topo", "") or t.get("coluna", "")
    frame = 2 if side == "atual" and end == "atual" else 4
    for letter, folder, dst_name in ((side, "tank_side", "rocket_fuel_tank_side_ct"), (end, "tank_end", "rocket_fuel_tank_end")):
        if not letter:
            notes.append(f"{dst_name}: sem escolha, mantém")
            continue
        src = VAR / folder / f"{letter}.png"
        if not src.exists():
            errors.append(f"{dst_name}: variante {letter} não existe ({src})")
            continue
        dst = RES / "textures/block" / f"{dst_name}.png"
        if letter == "atual" and frame == 4:
            ops.append((tank_ct.to_ct(Image.open(src).convert("RGBA")), dst))
            notes.append(f"{dst_name}: 'atual' convertida para moldura 4")
        else:
            ops.append((src, dst))
    if side or end:
        notes.append(f"tanque: gere as conectadas com --frame {frame}")

    # ---- motores ----
    for size in ("2x2", "3x3"):
        g = esc.get(f"motor_{size}", {})
        p = g.get("modelo", "")
        if not p:
            notes.append(f"rocket_engine_{size}: modelo sem escolha, mantém")
        else:
            src = SRC / f"rocket_engine_{size}_{p.upper()}.json"
            if not src.exists():
                errors.append(f"rocket_engine_{size}: proposta {p} não existe ({src})")
            else:
                ops.append((src, RES / "models/block" / f"rocket_engine_{size}.json"))
        for k in ENGINE_KEYS:
            letter = g.get(f"tex.{k}", "")
            name = f"rocket_engine_{size}_{k}"
            if not letter:
                notes.append(f"{name}: sem escolha, mantém")
                continue
            src = VAR / f"engine_{k}" / f"{letter}.png"
            if not src.exists():
                errors.append(f"{name}: variante {letter} não existe ({src})")
                continue
            ops.append((src, RES / "textures/block" / f"{name}.png"))

    if errors:
        raise SystemExit("nada gravado:\n  " + "\n  ".join(errors))
    for src, dst in ops:
        label = "convertida" if isinstance(src, Image.Image) else src
        print(("[dry-run] " if dry else "") + f"{label} -> {dst}")
        if dry:
            continue
        dst.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(src, Image.Image):
            src.save(dst)
        else:
            shutil.copyfile(src, dst)
    for n in notes:
        print("nota:", n)
    if not dry:
        rec = {"fonte": str(path), "frame_tanque": frame, "escolhas": esc}
        (HERE / "escolhas_aplicadas.json").write_text(json.dumps(rec, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
