"""Monta a pasta da página de escolha dos multiblocos: art/_work/escolha_multiblocos/ (imagens + escolha.json no
formato do escolha.py da skill minecraft-entity).

Uso (raiz do repo), depois de run_jobs.py e build_engines.py:
  python art/multiblocos/build_escolha.py
  python ~/.claude/skills/minecraft-entity/scripts/escolha.py serve art/_work/escolha_multiblocos

Grupos e perguntas
  tanque     tex.lado  (textura, mosaico)  lado conectado: ladrilho 8x8 + moldura de 4 px (tank_ct.py); extra = prévia
             tex.topo  (textura, mosaico)  topo/fundo no mesmo formato
             coluna    (modelo)            uma opção por topo; a imagem é a coluna 2x2x8 e 3x3x10 com o LADO escolhido
                                           (previa). Se tex.topo ficar em branco, a opção escolhida aqui vale como topo.
             "atual" = texturas atuais do tanque, sem conversão (prévia com --frame 2: mostra as emendas).
  motor_2x2 / motor_3x3
             modelo    (modelo)            propostas A e B; previa = render com a tubeira escolhida
             tex.nozzle / tex.exit / tex.plate / tex.pump (textura, mosaico); extras = render das propostas A e B com
                                           aquela variante (as outras texturas na letra A).
Letras: candidatas de candidatos.py na ordem (voto 1..4, BOX 1..4), menos as de EXCLUIR; o rótulo diz a origem.
Arquivos: var/<item>/<L>.png (textura pronta para aplicar; é o que apply_escolha.py copia), render/, previa/.
"""
import json
import shutil
import string
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import candidatos  # noqa: E402
import iso  # noqa: E402
import render_engine  # noqa: E402
import tank_ct  # noqa: E402

OUT = Path("art/_work/escolha_multiblocos")
TEX = Path("src/main/resources/assets/spacehorizons/textures/block")
SRC = HERE / "models" / "src"

# candidatas visivelmente quebradas (lisas ou borradas demais), fora da página
EXCLUIR = {"tank_side_isogrid": ["voto 4"], "engine_exit": ["voto 3"], "engine_pump": ["voto 2"]}

ENGINE_TEX = [("nozzle", "engine_nozzle", "Tubeira (sino e câmara)"),
              ("exit", "engine_exit", "Boca da tubeira (vista de baixo, esticada sobre a boca toda)"),
              ("plate", "engine_plate", "Placa de montagem e estrutura"),
              ("pump", "engine_pump", "Turbobombas e tubos")]
PROPOSTAS = {
    "2x2": {"A": "Clássico: placa cheia, domo, sino largo, duas turbobombas verticais com escapes",
            "B": "Treliça: quadro aberto com cruz, câmara alta com coletor, sino longo com anel, turbobomba deitada"},
    "3x3": {"A": "Clássico grande: placa cheia, domo, sino de 13 camadas com anel na boca, duas turbobombas",
            "B": "Estrutura cônica em degraus, quatro pernas e quadro, sino com dois anéis, duas turbobombas deitadas"},
}


def lettered(job):
    skip = set(EXCLUIR.get(job, []))
    c = [(l, im) for l, im in candidatos.candidates(job) if l not in skip]
    return [(string.ascii_uppercase[i], l, im) for i, (l, im) in enumerate(c)]


def save_big(im, path, size=256):
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)


def rel(p):
    return p.relative_to(OUT).as_posix()


def tank_group():
    sides = []
    for job in ("tank_side_branco", "tank_side_isogrid"):
        for _, label, im in lettered(job):
            sides.append((f"{job.split('_')[-1]} {label}", tank_ct.to_ct(im)))
    sides = [(string.ascii_uppercase[i], l, im) for i, (l, im) in enumerate(sides)]
    ends = [(L, l, tank_ct.to_ct(im)) for L, l, im in lettered("tank_end")]
    cur_side = Image.open(TEX / "rocket_fuel_tank.png").convert("RGBA")
    cur_end = Image.open(TEX / "rocket_fuel_tank_top.png").convert("RGBA")

    side_opts, end_opts, col_opts = [], [], []
    def_end = ends[0][2]
    def_side = sides[0][2]
    for L, label, im in sides:
        p = OUT / "var/tank_side" / f"{L}.png"
        save_big(im, p)
        pv = OUT / "previa/tank_side" / f"{L}.png"
        pv.parent.mkdir(parents=True, exist_ok=True)
        tank_ct.column_preview(im, def_end, scale=2).save(pv)
        side_opts.append({"valor": L, "rotulo": label, "imagens": [rel(p), rel(pv)]})
    pa = OUT / "var/tank_side/atual.png"
    shutil.copyfile(TEX / "rocket_fuel_tank.png", pa)
    pva = OUT / "previa/tank_side/atual.png"
    tank_ct.column_preview(cur_side, cur_end, frame=2, scale=2).save(pva)
    side_opts.append({"valor": "atual", "rotulo": "textura atual, sem conversão (--frame 2, com emendas)",
                      "imagens": [rel(pa), rel(pva)]})

    for L, label, im in ends:
        p = OUT / "var/tank_end" / f"{L}.png"
        save_big(im, p)
        pv = OUT / "previa/tank_end" / f"{L}.png"
        pv.parent.mkdir(parents=True, exist_ok=True)
        tank_ct.column_preview(def_side, im, scale=2).save(pv)
        end_opts.append({"valor": L, "rotulo": label, "imagens": [rel(p), rel(pv)]})
    pa = OUT / "var/tank_end/atual.png"
    shutil.copyfile(TEX / "rocket_fuel_tank_top.png", pa)
    end_opts.append({"valor": "atual", "rotulo": "topo atual, sem conversão", "imagens": [rel(pa)]})

    # coluna: cada topo com cada lado
    all_sides = [(L, im, 4) for L, _, im in sides] + [("atual", cur_side, 2)]
    all_ends = [(L, im) for L, _, im in ends] + [("atual", cur_end)]
    for E, eim in all_ends:
        for S, sim, fr in all_sides:
            p = OUT / "previa/coluna" / f"{E}_{S}.png"
            p.parent.mkdir(parents=True, exist_ok=True)
            frame = 2 if (S == "atual" and E == "atual") else 4
            s_img = sim if frame == 2 or S != "atual" else tank_ct.to_ct(sim)
            e_img = eim if frame == 2 or E != "atual" else tank_ct.to_ct(eim)
            tank_ct.column_preview(s_img, e_img, frame=frame, scale=3).save(p)
        col_opts.append({"valor": E, "rotulo": f"topo {E}: coluna 2x2x8 e 3x3x10 com o lado escolhido",
                         "imagens": [rel(OUT / "previa/coluna" / f"{E}_{sides[0][0]}.png")],
                         "previa": {"pergunta": "tex.lado", "modelo": "previa/coluna/" + E + "_{v}.png"}})
    return {
        "id": "tanque", "titulo": "Tanque multibloco (texturas conectadas)",
        "nota": "Lado e topo no formato do tools/gen_connected_block.py com --frame 4 (ladrilho 8x8 sem emenda + moldura "
                "lisa de 4 px). A última pergunta mostra a coluna montada com o lado escolhido e cada topo; se 'Topo' "
                "ficar em branco, o topo escolhido lá vale. Misturar 'atual' com uma variante converte a atual para o "
                "formato de moldura 4.",
        "perguntas": [
            {"id": "tex.lado", "titulo": "Lado (rocket_fuel_tank_side_ct)", "tipo": "textura", "mosaico": True, "opcoes": side_opts},
            {"id": "tex.topo", "titulo": "Topo e fundo (rocket_fuel_tank_end)", "tipo": "textura", "mosaico": True, "opcoes": end_opts},
            {"id": "coluna", "titulo": "Coluna montada (prévia com o lado escolhido)", "tipo": "modelo", "opcoes": col_opts},
        ]}


def engine_group(size):
    texs = {}
    for key, job, _ in ENGINE_TEX:
        texs[key] = lettered(job)
        for L, label, im in texs[key]:
            save_big(im, OUT / f"var/engine_{key}" / f"{L}.png")
    default = {key: OUT / f"var/engine_{key}" / f"{texs[key][0][0]}.png" for key in texs}
    models = {P: json.loads((SRC / f"rocket_engine_{size}_{P}.json").read_text(encoding="utf-8")) for P in "AB"}
    rdir = OUT / f"render/motor_{size}"
    rdir.mkdir(parents=True, exist_ok=True)
    perguntas = []
    opts = []
    for P, model in models.items():
        for L, _, _ in texs["nozzle"]:
            t = dict(default, nozzle=OUT / "var/engine_nozzle" / f"{L}.png")
            render_engine.render_engine(model, t).save(rdir / f"{P}_nozzle_{L}.png")
        opts.append({"valor": P, "rotulo": PROPOSTAS[size][P],
                     "imagens": [rel(rdir / f"{P}_nozzle_{texs['nozzle'][0][0]}.png")],
                     "previa": {"pergunta": "tex.nozzle", "modelo": rel(rdir) + f"/{P}_nozzle_" + "{v}.png"}})
    perguntas.append({"id": "modelo", "titulo": f"Forma do motor {size} (vista de cima e de baixo)", "tipo": "modelo", "opcoes": opts})
    for key, job, title in ENGINE_TEX:
        topts = []
        for L, label, _ in texs[key]:
            imgs = [rel(OUT / f"var/engine_{key}" / f"{L}.png")]
            for P, model in models.items():
                f = rdir / f"{P}_{key}_{L}.png"
                if not f.exists():
                    render_engine.render_engine(model, dict(default, **{key: OUT / f"var/engine_{key}" / f"{L}.png"})).save(f)
                imgs.append(rel(f))
            topts.append({"valor": L, "rotulo": f"{label} — extras: propostas A e B", "imagens": imgs})
        perguntas.append({"id": f"tex.{key}", "titulo": f"{title} (rocket_engine_{size}_{key})", "tipo": "textura",
                          "mosaico": True, "opcoes": topts})
    base = "base 2x2, altura 2; o bloco mestre é o canto de menor x/y/z (modelo em 0..32)" if size == "2x2" else \
        "base 3x3, altura 3; o bloco mestre é o do centro (modelo em -16..32)"
    return {"id": f"motor_{size}", "titulo": f"Motor multibloco {size}",
            "nota": f"Modelo da estrutura formada ({base}). As renders da forma usam a tubeira escolhida e as outras "
                    "texturas na letra A; as extras de cada textura mostram as duas propostas com aquela variante.",
            "perguntas": perguntas}


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    if OUT.exists():
        for sub in ("var", "render", "previa"):
            shutil.rmtree(OUT / sub, ignore_errors=True)
    OUT.mkdir(parents=True, exist_ok=True)
    man = {"titulo": "Multiblocos: tanque e motores",
           "descricao": "Tanque com texturas conectadas e motores 2x2 e 3x3 (modelo da estrutura formada). "
                        "Clique para escolher; cada clique fica salvo. Em branco = o agente decide.",
           "grupos": [tank_group(), engine_group("2x2"), engine_group("3x3")]}
    (OUT / "escolha.json").write_text(json.dumps(man, ensure_ascii=False, indent=1), encoding="utf-8")
    n = sum(1 for _ in OUT.rglob("*.png"))
    for g in man["grupos"]:
        print(g["id"], {q["id"]: len(q["opcoes"]) for q in g["perguntas"]})
    print(f"{OUT}/escolha.json, {n} imagens")


if __name__ == "__main__":
    main()
