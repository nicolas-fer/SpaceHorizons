"""Monta a pasta da página de escolha do Tier 2: art/_work/escolha_tier2/ (imagens + escolha.json no formato do
escolha.py da skill minecraft-entity). Um grupo por bloco/item do contrato (docs/TIER2_IDS.md).

Uso (raiz do repo), depois de run_jobs.py e build_models.py:
  python art/tier2/build_escolha.py
  python ~/.claude/skills/minecraft-entity/scripts/escolha.py serve art/_work/escolha_tier2

Letras: candidatas na ordem (gerador: voto 1..3 e depois média de área "BOX" 1..3 dos mesmos brutos; recolor:
hue shifting suave/médio/forte, depois a cor alternativa), menos as de EXCLUIR. O rótulo diz a origem.
Arquivos: var/<id da textura>/<L>.png (textura pronta, é o que apply_escolha.py copia), render/, previa/.

Grupos especiais
  mars_titanium_ore  veios de minério (máscara do iron_ore vanilla, recolorida) sobre o regolito: a letra escolhida é
                     o estilo do veio; o apply_escolha.py compõe sobre o regolito escolhido (imagens extras = veio sobre
                     cada regolito).
  hydrogen_tank / xenon_tank  lado e topo no formato de moldura 4 (art/multiblocos/tank_ct.py) + coluna montada.
  rtg_generator      modelo A/B (prévia com o lado escolhido) + lado + topo.
  <motor>            modelo_1x1, modelo_2x2, modelo_3x3 (prévia com a textura principal escolhida) + as 4 texturas
                     (extras: renders do 2x2 A e B com aquela variante; as outras texturas na letra A).
"""
import json
import shutil
import string
import sys
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import numpy as np
from PIL import Image, ImageEnhance

HERE = Path(__file__).resolve().parent
SKILL = Path.home() / ".claude/skills/minecraft-entity/scripts"
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "multiblocos"))
sys.path.insert(0, str(SKILL))
import build_models  # noqa: E402
import pixelize as px  # noqa: E402
import recolor as rc  # noqa: E402
import tank_ct  # noqa: E402

OUT = Path("art/_work/escolha_tier2")
WORK = Path("art/_work/tier2/out/_texturas")
SRC = HERE / "models" / "src"
TEX = Path("src/main/resources/assets/spacehorizons/textures")

# candidatas visivelmente quebradas, fora da página (rótulos como "voto 2", "BOX 3")
EXCLUIR = {}

# Opções sem gerador (recolor) para as texturas dos motores iônico e de fusão: a cota do Gemini acabou antes desses
# jobs (429, limite mensal de gasto). Base = textura do mod (3 forças de hue shifting) ou "job:<nome>" (as variantes
# por voto de outro job já gerado, força média). Ficam depois das candidatas do gerador, quando ele rodar.
B = "src/main/resources/assets/spacehorizons/textures/block/"
FALLBACK = {
    "ion_grid": [("job:nuclear_exit", "#2E8CFF"), (B + "rocket_engine_2x2_exit.png", "#2E8CFF")],
    "ion_body": [(B + "copper_tank.png", "#D4A93C"), (B + "rocket_engine_2x2_pump.png", "#D4A93C")],
    "ion_plate": [("job:nuclear_plate", "#AEB8C4"), (B + "machine_casing.png", "#AEB8C4")],
    "ion_pipe": [(B + "rocket_engine_2x2_pump.png", "#7C5CC4"), ("job:nuclear_plate", "#6E5A9C")],
    "fusion_ring": [(B + "copper_tank.png", "#C8743A"), (B + "rocket_engine_2x2_pump.png", "#C8743A")],
    "fusion_body": [("job:nuclear_plate", "#4A4560"), (B + "machine_casing.png", "#4A4560")],
    "fusion_plasma": [("job:nuclear_exit", "#B070FF"), (B + "rocket_engine_2x2_exit.png", "#B070FF")],
    "fusion_plate": [("job:nuclear_plate", "#3E4658"), (B + "machine_casing.png", "#3E4658")],
}


def fallback(job):
    out = []
    for base, cor in FALLBACK.get(job, []):
        if base.startswith("job:"):
            src = base[4:]
            for p in sorted((WORK / src / "variantes").glob("*.png"), key=lambda p: int(p.stem)):
                im = Image.open(p).convert("RGBA")
                out.append((f"recolor de {src} {p.stem} ({cor})", rc.recolor(im, cor, None, 1.0).convert("RGBA")))
        else:
            im = Image.open(base).convert("RGBA")
            for shift, n in ((0.5, "suave"), (1.0, "médio"), (1.6, "forte")):
                out.append((f"recolor de {Path(base).stem} {n} ({cor})", rc.recolor(im, cor, None, shift).convert("RGBA")))
    return out

ENGINE_TITLES = {
    "nuclear_engine": ("Motor nuclear térmico", {"core": "Blindagem do reator (com símbolo de radiação)",
                                                  "nozzle": "Bocal longo", "exit": "Boca do bocal (vista de baixo)",
                                                  "plate": "Estrutura e tubos de hidrogênio"}),
    "ion_engine": ("Propulsor iônico", {"grid": "Grade de íons (boca, vista de baixo)", "body": "Carcaça",
                                        "plate": "Estrutura de montagem", "pipe": "Eletrônica e linhas de xenônio"}),
    "fusion_engine": ("Motor de fusão", {"ring": "Bobinas magnéticas", "body": "Câmara de contenção",
                                         "plasma": "Plasma (coluna e boca)", "plate": "Estrutura de montagem"}),
}
PROPOSTAS = {
    "nuclear_engine": {"A": "Clássico: escudo, reator alto, bocal longo e estreito, tubos de H2 e turbobomba",
                       "B": "Reator com oito tambores de controle e cintas, bocal mais longo com dois anéis"},
    "ion_engine": {"A": "Propulsor único raso e largo, grade na boca toda, PPU e tanques de xenônio em cima",
                   "B": "Cacho de quatro propulsores com grade própria e PPU central"},
    "fusion_engine": {"A": "Bocal magnético: câmara com toro, coluna de plasma entre três bobinas abertas",
                      "B": "Toro quadrado largo com grampos, coluna de plasma mais grossa e bobinas largas"},
    "rtg_generator": {"A": "Cilindro com oito aletas em cruz e tampa redonda",
                      "B": "Caixa com doze aletas em pente, faixa de alerta e tampa quadrada"},
}
SIZE_NOTE = {"1x1": "bloco avulso (modelo 0..16)",
             "2x2": "estrutura 2x2x2, mestre no canto de menor x/y/z (modelo 0..32)",
             "3x3": "estrutura 3x3x3, mestre no centro (modelo -16..32)"}


# ---------------------------------------------------------------- candidatas
def box16(raw_path, colors=7):
    img = np.asarray(Image.open(raw_path).convert("RGB")).astype(float)
    bg = px.corner_color(img)
    work = px.crop_border(img, bg, 48)
    im = Image.fromarray(np.clip(work, 0, 255).astype(np.uint8)).resize((16, 16), Image.BOX)
    im = ImageEnhance.Contrast(im).enhance(1.15)
    im = ImageEnhance.Color(im).enhance(1.1)
    return im.quantize(colors=colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGBA")


def candidates(job, box=True):
    """[(rótulo, imagem 16x16)] de um job: variantes do texture.py, BOX dos brutos e o recolor alternativo."""
    out = []
    for sub, tag in ((job, ""), (job + "_alt", "alt ")):
        d = WORK / sub
        for p in sorted((d / "variantes").glob("*.png"), key=lambda p: int(p.stem)):
            kind = "recolor" if not (d / "raw").exists() else "voto"
            out.append((f"{tag}{kind} {p.stem}", Image.open(p).convert("RGBA")))
        if box and (d / "raw").exists():
            for p in sorted((d / "raw").glob("raw_*.png")):
                out.append((f"BOX {p.stem.split('_')[1]}", box16(p)))
    out += fallback(job)
    seen, keep, skip = set(), [], set(EXCLUIR.get(job, []))
    for label, im in out:
        if label in skip:
            continue
        a = np.asarray(im)
        key = a.tobytes()
        if key in seen or len({tuple(c) for c in a.reshape(-1, 4)[:, :3]}) < 3:
            continue
        seen.add(key)
        keep.append((label, im))
    return keep


def lettered(items):
    return [(string.ascii_uppercase[i], label, im) for i, (label, im) in enumerate(items)]


def rel(p):
    return Path(p).relative_to(OUT).as_posix()


def save(im, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    return path


def tex_question(qid, title, opts, mosaico=True):
    return {"id": qid, "titulo": title, "tipo": "textura", "mosaico": mosaico, "opcoes": opts}


def simple_group(gid, title, jobs, folder, mosaico, nota=None, box=True):
    """Grupo de uma textura só: candidatas dos jobs (na ordem) gravadas em var/<folder>/<L>.png."""
    items = []
    for job, prefix in jobs:
        items += [((prefix + " " if prefix else "") + label, im) for label, im in candidates(job, box)]
    opts = []
    for L, label, im in lettered(items):
        p = save(im, OUT / "var" / folder / f"{L}.png")
        opts.append({"valor": L, "rotulo": label, "imagens": [rel(p)]})
    g = {"id": gid, "titulo": title, "perguntas": [tex_question("tex", f"Textura ({folder.split('/')[-1]})", opts, mosaico)]}
    if nota:
        g["nota"] = nota
    return g


# ---------------------------------------------------------------- minério
def ore_layers():
    """Máscara dos veios do iron_ore vanilla (pixels diferentes do stone) e estilos de veio recoloridos."""
    import vanilla
    proj = vanilla.find_project(Path.cwd())
    ore = vanilla.load(proj, "block/iron_ore").convert("RGBA")
    stone = vanilla.load(proj, "block/stone").convert("RGBA")
    a, b = np.asarray(ore).astype(int), np.asarray(stone).astype(int)
    mask = np.abs(a - b).sum(-1) > 30
    styles = []
    for cor, nome in (("#C9D3DE", "titânio claro"), ("#E3E8EE", "titânio quase branco")):
        for shift, sname in ((0.5, "suave"), (1.0, "médio"), (1.6, "forte")):
            styles.append((f"veio {nome}, {sname}", np.asarray(rc.recolor(ore, cor, None, shift).convert("RGBA"))))
    return mask, styles


def compose_ore(regolith, veins, mask):
    out = np.asarray(regolith.convert("RGBA")).copy()
    out[mask] = veins[mask]
    return Image.fromarray(out, "RGBA")


def ore_group(regoliths):
    mask, styles = ore_layers()
    opts = []
    for i, (label, veins) in enumerate(styles):
        L = string.ascii_uppercase[i]
        imgs = []
        for R, _, rim in regoliths:
            imgs.append(rel(save(compose_ore(rim, veins, mask), OUT / "var/mars_titanium_ore" / f"{L}_{R}.png")))
        opts.append({"valor": L, "rotulo": label + " — principal sobre o regolito A; extras: sobre B, C, ...",
                     "imagens": imgs})
    return {"id": "mars_titanium_ore", "titulo": "Minério de titânio marciano (mars_titanium_ore)",
            "nota": "Veios na forma do iron_ore vanilla, recoloridos em titânio, sobre o regolito. O apply_escolha.py "
                    "compõe o estilo escolhido aqui sobre o regolito escolhido no grupo mars_regolith.",
            "perguntas": [tex_question("tex", "Estilo do veio", opts)]}


# ---------------------------------------------------------------- tanques
def tile_of_ct(img):
    """Ladrilho 8x8 de uma textura que já está no formato de moldura 4 (miolo 4..11)."""
    a = np.asarray(img.convert("RGBA"))
    t = np.zeros((8, 8, 4), np.uint8)
    for y in range(4, 12):
        for x in range(4, 12):
            t[y % 8, x % 8] = a[y, x]
    return t


def recolored_tile(ct_img, cor, shift):
    """Ladrilho do tanque atual (miolo da moldura 4) recolorido sozinho, sem a moldura entrar na rampa."""
    t = tile_of_ct(ct_img)
    tiled = Image.fromarray(np.tile(t, (2, 2, 1)), "RGBA")
    return np.asarray(rc.recolor(tiled, cor, None, shift).convert("RGBA"))[:8, :8].copy()


def tank_group(tid, title, side_job, end_job, cor):
    """Opções do gerador (convertidas para moldura 4) e o ladrilho do rocket_fuel_tank atual recolorido em `cor`
    (só o miolo entra no recolor; recolorir a textura inteira escurece o ladrilho)."""
    cur_side = Image.open(TEX / "block/rocket_fuel_tank_frame.png").convert("RGBA")
    cur_end = Image.open(TEX / "block/rocket_fuel_tank_frame_end.png").convert("RGBA")
    sides = [(label, tank_ct.to_ct(im)) for label, im in candidates(side_job)]
    ends = [(label, tank_ct.to_ct(im)) for label, im in candidates(end_job)]
    for shift, n in ((0.5, "suave"), (1.0, "médio"), (1.6, "forte")):
        sides.append((f"tanque atual recolorido, {n}", tank_ct.to_ct(cur_side, recolored_tile(cur_side, cor, shift))))
        ends.append((f"topo atual recolorido, {n}", tank_ct.to_ct(cur_end, recolored_tile(cur_end, cor, shift))))
    sides, ends = lettered(sides), lettered(ends)
    fs, fe = f"{tid}_frame", f"{tid}_frame_end"
    side_opts, end_opts, col_opts = [], [], []
    for L, label, im in sides:
        p = save(im, OUT / "var" / fs / f"{L}.png")
        pv = save(tank_ct.column_preview(im, ends[0][2], scale=2), OUT / "previa" / fs / f"{L}.png")
        side_opts.append({"valor": L, "rotulo": label + " — extra: coluna com o topo A", "imagens": [rel(p), rel(pv)]})
    for L, label, im in ends:
        p = save(im, OUT / "var" / fe / f"{L}.png")
        pv = save(tank_ct.column_preview(sides[0][2], im, scale=2), OUT / "previa" / fe / f"{L}.png")
        end_opts.append({"valor": L, "rotulo": label + " — extra: coluna com o lado A", "imagens": [rel(p), rel(pv)]})
    for E, _, eim in ends:
        for S, _, sim in sides:
            save(tank_ct.column_preview(sim, eim, scale=3), OUT / "previa" / f"{tid}_coluna" / f"{E}_{S}.png")
        col_opts.append({"valor": E, "rotulo": f"topo {E}: coluna 2x2x8 e 3x3x10 com o lado escolhido",
                         "imagens": [f"previa/{tid}_coluna/{E}_{sides[0][0]}.png"],
                         "previa": {"pergunta": "tex.lado", "modelo": f"previa/{tid}_coluna/{E}_" + "{v}.png"}})
    return {"id": tid, "titulo": title,
            "nota": "Lado e topo no formato do tools/gen_connected_block.py com --frame 4 (ladrilho 8x8 sem emenda + "
                    "moldura lisa de 4 px, a mesma do rocket_fuel_tank). A última pergunta mostra a coluna montada com o "
                    "lado escolhido e cada topo; se 'Topo' ficar em branco, vale o topo escolhido lá.",
            "perguntas": [
                tex_question("tex.lado", f"Lado ({fs})", side_opts),
                tex_question("tex.topo", f"Topo e fundo ({fe})", end_opts),
                {"id": "coluna", "titulo": "Coluna montada (prévia com o lado escolhido)", "tipo": "modelo", "opcoes": col_opts},
            ]}


# ---------------------------------------------------------------- renders
def _render(task):
    import render
    model_path, textures, out = task
    model = json.loads(Path(model_path).read_text(encoding="utf-8"))
    scale = 14 if "rtg" in Path(model_path).name else None
    out.parent.mkdir(parents=True, exist_ok=True)
    render.render_model(model, textures, scale=scale).save(out)
    return out


def model_group(gid, title, keys, main, models, nota):
    """keys: [(chave, job, título, pasta var)]. models: {(tam, P): caminho .json}. Devolve (grupo, tarefas de render)."""
    texs, tasks = {}, []
    for key, job, _, folder in keys:
        texs[key] = lettered(candidates(job))
        for L, _, im in texs[key]:
            save(im, OUT / "var" / folder / f"{L}.png")
    path = {key: (lambda L, f=folder: OUT / "var" / f / f"{L}.png") for key, _, _, folder in keys}
    default = {key: path[key](texs[key][0][0]) for key in texs}
    rdir = OUT / "render" / gid
    perguntas = []
    sizes = sorted({s for s, _ in models}, key=lambda s: ["1x1", "2x2", "3x3", "-"].index(s))
    for size in sizes:
        opts = []
        for P in "AB":
            mp = models[(size, P)]
            for L, _, _ in texs[main]:
                tasks.append((str(mp), dict(default, **{main: path[main](L)}), rdir / f"{size}_{P}_{main}_{L}.png"))
            opts.append({"valor": P, "rotulo": PROPOSTAS[gid][P],
                         "imagens": [rel(rdir / f"{size}_{P}_{main}_{texs[main][0][0]}.png")],
                         "previa": {"pergunta": f"tex.{main}", "modelo": rel(rdir) + f"/{size}_{P}_{main}_" + "{v}.png"}})
        qid = "modelo" if size == "-" else f"modelo_{size}"
        qt = "Forma (vista de cima e de baixo)" if size == "-" else f"Forma {size}: {SIZE_NOTE[size]} (vista de cima e de baixo)"
        perguntas.append({"id": qid, "titulo": qt, "tipo": "modelo", "opcoes": opts})
    ref_size = "2x2" if "2x2" in sizes else sizes[0]
    for key, job, ktitle, folder in keys:
        topts = []
        for L, label, _ in texs[key]:
            imgs = [rel(path[key](L))]
            for P in "AB":
                f = rdir / f"{ref_size}_{P}_{key}_{L}.png"
                if key != main:
                    tasks.append((str(models[(ref_size, P)]), dict(default, **{key: path[key](L)}), f))
                imgs.append(rel(f))
            topts.append({"valor": L, "rotulo": f"{label} — extras: propostas A e B", "imagens": imgs})
        perguntas.append(tex_question(f"tex.{key}", f"{ktitle} ({folder})", topts))
    return {"id": gid, "titulo": title, "nota": nota, "perguntas": perguntas}, tasks


def engine_group(engine):
    title, ktitles = ENGINE_TITLES[engine]
    pre = engine.split("_")[0]
    keys = [(k, f"{pre}_{k}", ktitles[k], f"{engine}_{k}") for k in build_models.KEYS[engine]]
    models = {(s, P): SRC / f"{engine}_{s}_{P}.json" for s in ("1x1", "2x2", "3x3") for P in "AB"}
    nota = ("Bloco avulso e modelos da estrutura formada (2x2: mestre no canto mínimo, 0..32; 3x3: mestre no centro, "
            "-16..32). As mesmas texturas servem aos três tamanhos. As renders das formas usam a textura principal "
            "escolhida e as outras na letra A; as extras de cada textura mostram o 2x2 A e B com aquela variante.")
    return model_group(engine, f"{title} ({engine})", keys, build_models.MAIN[engine], models, nota)


def rtg_group():
    keys = [("side", "rtg_side", "Lado: aletas e corpo", "rtg_generator"),
            ("top", "rtg_top", "Topo", "rtg_generator_top")]
    models = {("-", P): SRC / f"rtg_generator_{P}.json" for P in "AB"}
    nota = "Bloco de 1x1 com modelo próprio; usa também machine_casing (base e tampa) e hazard_stripes (faixa)."
    return model_group("rtg_generator", "Gerador radioisotópico (rtg_generator)", keys, "side", models, nota)


# ---------------------------------------------------------------- main
def main():
    sys.stdout.reconfigure(encoding="utf-8")
    for sub in ("var", "render", "previa"):
        shutil.rmtree(OUT / sub, ignore_errors=True)
    OUT.mkdir(parents=True, exist_ok=True)
    grupos, tasks = [], []

    g = simple_group("mars_regolith", "Regolito marciano (mars_regolith)", [("mars_regolith", "")], "mars_regolith", True)
    grupos.append(g)
    regs = [(o["valor"], o["rotulo"], Image.open(OUT / o["imagens"][0])) for o in g["perguntas"][0]["opcoes"]]
    grupos.append(ore_group(regs))
    grupos.append(simple_group("titanium_block", "Bloco de titânio (titanium_block)",
                               [("titanium_block", "iron_block"), ("titanium_block_aco", "bloco de aço do mod")],
                               "titanium_block", True))
    for gid, title, jobs in (
            ("raw_titanium", "Titânio bruto (raw_titanium)", [("raw_titanium", "")]),
            ("titanium_ingot", "Lingote de titânio (titanium_ingot)", [("titanium_ingot", "")]),
            ("titanium_plate", "Placa de titânio (titanium_plate)", [("titanium_plate", "")]),
            ("rare_earth_dust", "Pó de terras raras (rare_earth_dust)",
             [("rare_earth_dust", "redstone"), ("rare_earth_dust_glow", "glowstone_dust")]),
            ("helium3_canister", "Galão de hélio-3 (helium3_canister)", [("helium3_canister", "")]),
            ("hydrogen_canister", "Galão de hidrogênio (hydrogen_canister)", [("hydrogen_canister", "")]),
            ("xenon_canister", "Galão de xenônio (xenon_canister)", [("xenon_canister", "")]),
            ("nuclear_fuel_rod", "Barra de combustível nuclear (nuclear_fuel_rod)", [("nuclear_fuel_rod", "")]),
            ("superconductor_coil", "Bobina supercondutora (superconductor_coil)", [("superconductor_coil", "")])):
        grupos.append(simple_group(gid, title, jobs, gid, False, box=False))
    grupos.append(tank_group("hydrogen_tank", "Tanque de hidrogênio (hydrogen_tank)", "hydrogen_tank_side",
                             "hydrogen_tank_end", "#A8D8EE"))
    grupos.append(tank_group("xenon_tank", "Tanque de xenônio (xenon_tank)", "xenon_tank_side",
                             "xenon_tank_end", "#8E6CC8"))
    g, t = rtg_group()
    grupos.append(g)
    tasks += t
    for engine in ENGINE_TITLES:
        g, t = engine_group(engine)
        grupos.append(g)
        tasks += t

    seen, uniq = set(), []
    for task in tasks:
        if task[2] not in seen:
            seen.add(task[2])
            uniq.append(task)
    print(f"{len(uniq)} renders...", flush=True)
    with ProcessPoolExecutor(6) as ex:
        list(ex.map(_render, uniq, chunksize=4))

    man = {"titulo": "Tier 2: blocos, itens, tanques e motores",
           "descricao": "Texturas e modelos do Tier 2 (ids de docs/TIER2_IDS.md). Clique para escolher; cada clique fica "
                        "salvo. Em branco = o agente decide.",
           "grupos": grupos}
    (OUT / "escolha.json").write_text(json.dumps(man, ensure_ascii=False, indent=1), encoding="utf-8")
    missing = [i for g in grupos for q in g["perguntas"] for o in q["opcoes"] for i in o["imagens"]
               if not (OUT / i).exists()]
    for g in grupos:
        print(g["id"], {q["id"]: len(q["opcoes"]) for q in g["perguntas"]})
    print(f"{OUT}/escolha.json, {sum(1 for _ in OUT.rglob('*.png'))} imagens, faltando: {missing[:5]}")


if __name__ == "__main__":
    main()
