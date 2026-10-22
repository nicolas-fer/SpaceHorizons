"""Aplica a escolha do Tier 2 (escolhas.json do escolha.py) em assets/, com os ids de docs/TIER2_IDS.md.

Uso (raiz do repo):
  python art/tier2/apply_escolha.py [art/_work/escolha_tier2/escolhas.json] [--dry-run] [--ct]

Gravado em src/main/resources/assets/spacehorizons/:
  textures/block/  mars_regolith, mars_titanium_ore (veio escolhido sobre o regolito escolhido), titanium_block,
                   hydrogen_tank_frame, hydrogen_tank_frame_end, xenon_tank_frame, xenon_tank_frame_end (bases do
                   gen_connected_block.py, moldura 4), rtg_generator, rtg_generator_top,
                   <motor>_<chave> (4 por motor) e <motor> (cópia da textura principal: partícula e ícones)
  textures/item/   raw_titanium, titanium_ingot, titanium_plate, rare_earth_dust, helium3_canister, hydrogen_canister,
                   xenon_canister, nuclear_fuel_rod, superconductor_coil
  models/block/    rtg_generator, <motor> (bloco avulso), <motor>_2x2, <motor>_3x3
Motores: nuclear_engine (core, nozzle, exit, plate), ion_engine (grid, body, plate, pipe),
fusion_engine (ring, body, plasma, plate). Os modelos já apontam para spacehorizons:block/<motor>_<chave>.

Tanques: depois de aplicar, gere as variantes conectadas (ou passe --ct para rodar daqui):
  python tools/gen_connected_block.py --name hydrogen_tank --texture spacehorizons:block/hydrogen_tank_frame \
      --texture-end spacehorizons:block/hydrogen_tank_frame_end --frame 4
  (idem xenon_tank). Atenção: o script sobrescreve blockstates/<id>.json.
Topo do tanque: vale "tex.topo"; em branco, vale a opção da pergunta "coluna".
Pergunta em branco = não mexe naquele arquivo (avisa). Lê de art/_work/escolha_tier2/var/ (build_escolha.py).
"""
import json
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
VAR = Path("art/_work/escolha_tier2/var")
SRC = HERE / "models" / "src"
RES = Path("src/main/resources/assets/spacehorizons")

ITEMS = ("raw_titanium", "titanium_ingot", "titanium_plate", "rare_earth_dust", "helium3_canister",
         "hydrogen_canister", "xenon_canister", "nuclear_fuel_rod", "superconductor_coil")
BLOCKS = ("mars_regolith", "titanium_block")
ENGINES = {"nuclear_engine": (("core", "nozzle", "exit", "plate"), "core"),
           "ion_engine": (("grid", "body", "plate", "pipe"), "body"),
           "fusion_engine": (("ring", "body", "plasma", "plate"), "ring")}
TANKS = ("hydrogen_tank", "xenon_tank")


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry, ct = "--dry-run" in sys.argv, "--ct" in sys.argv
    path = Path(args[0] if args else "art/_work/escolha_tier2/escolhas.json")
    esc = json.loads(path.read_text(encoding="utf-8"))["escolhas"]
    ops, errors, notes = [], [], []

    def add(src, dst, label):
        if not src.exists():
            errors.append(f"{label}: não existe ({src})")
        else:
            ops.append((src, dst))

    def pick(group, q, label):
        v = esc.get(group, {}).get(q, "")
        if not v:
            notes.append(f"{label}: sem escolha, mantém")
        return v

    for gid in ITEMS:
        v = pick(gid, "tex", gid)
        if v:
            add(VAR / gid / f"{v}.png", RES / "textures/item" / f"{gid}.png", gid)
    for gid in BLOCKS:
        v = pick(gid, "tex", gid)
        if v:
            add(VAR / gid / f"{v}.png", RES / "textures/block" / f"{gid}.png", gid)

    vein = pick("mars_titanium_ore", "tex", "mars_titanium_ore")
    if vein:
        reg = esc.get("mars_regolith", {}).get("tex", "")
        if not reg:
            reg = "A"
            notes.append("mars_titanium_ore: regolito em branco, veio composto sobre o regolito A")
        add(VAR / "mars_titanium_ore" / f"{vein}_{reg}.png", RES / "textures/block/mars_titanium_ore.png",
            "mars_titanium_ore")

    for tid in TANKS:
        g = esc.get(tid, {})
        side, end = g.get("tex.lado", ""), g.get("tex.topo", "") or g.get("coluna", "")
        for v, name in ((side, f"{tid}_frame"), (end, f"{tid}_frame_end")):
            if not v:
                notes.append(f"{name}: sem escolha, mantém")
                continue
            add(VAR / name / f"{v}.png", RES / "textures/block" / f"{name}.png", name)

    v = pick("rtg_generator", "modelo", "rtg_generator (modelo)")
    if v:
        add(SRC / f"rtg_generator_{v}.json", RES / "models/block/rtg_generator.json", "rtg_generator")
    for q, name in (("tex.side", "rtg_generator"), ("tex.top", "rtg_generator_top")):
        v = pick("rtg_generator", q, name)
        if v:
            add(VAR / name / f"{v}.png", RES / "textures/block" / f"{name}.png", name)

    for eng, (keys, main) in ENGINES.items():
        g = esc.get(eng, {})
        for size, dst in (("1x1", eng), ("2x2", f"{eng}_2x2"), ("3x3", f"{eng}_3x3")):
            v = g.get(f"modelo_{size}", "")
            if not v:
                notes.append(f"{dst} (modelo): sem escolha, mantém")
                continue
            add(SRC / f"{eng}_{size}_{v}.json", RES / "models/block" / f"{dst}.json", dst)
        for k in keys:
            v = g.get(f"tex.{k}", "")
            name = f"{eng}_{k}"
            if not v:
                notes.append(f"{name}: sem escolha, mantém")
                continue
            add(VAR / name / f"{v}.png", RES / "textures/block" / f"{name}.png", name)
            if k == main:
                add(VAR / name / f"{v}.png", RES / "textures/block" / f"{eng}.png", eng + " (partícula)")

    if errors:
        raise SystemExit("nada gravado:\n  " + "\n  ".join(errors))
    for src, dst in ops:
        print(("[dry-run] " if dry else "") + f"{src} -> {dst}")
        if not dry:
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(src, dst)
    for n in notes:
        print("nota:", n)
    for tid in TANKS:
        cmd = [sys.executable, "tools/gen_connected_block.py", "--name", tid,
               "--texture", f"spacehorizons:block/{tid}_frame", "--texture-end", f"spacehorizons:block/{tid}_frame_end",
               "--frame", "4"]
        if ct and not dry:
            subprocess.run(cmd, check=True)
        else:
            print("tanque:", " ".join(cmd[1:]))
    if not dry:
        rec = {"fonte": str(path), "escolhas": esc}
        (HERE / "escolhas_aplicadas.json").write_text(json.dumps(rec, ensure_ascii=False, indent=2) + "\n",
                                                      encoding="utf-8")


if __name__ == "__main__":
    main()
