"""Monta a página de escolha das texturas: art/_work/escolha/index.html (+ escolhas_modelo.json).

Uso (raiz do repo), depois do run_jobs.py:  python art/textures/escolha.py [--sem-render]

Variantes de cada textura (copiadas para art/_work/escolha/var/<nome>/<letra>.png):
- gerador (block/item): A-D = as 4 variantes do texture.py (redução por voto); só blocos: E-H = os mesmos
  brutos 1-4 reduzidos por média de área (reduce16.py);
- recolor: A-C = hue shifting suave/médio/forte com "cor", D-F = o mesmo com "cor_alt";
- atual = a textura que está hoje em assets/.
Para as texturas usadas nos modelos de art/models/, renderiza o modelo (motor do MCP bbmodel, bb-render)
com cada variante no lugar da textura, mantendo as demais como estão.
A escolha vai em escolhas_modelo.json (troque "atual" pela letra) e é aplicada com apply_picks.py.
"""
import base64
import html
import json
import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
JOBS = HERE / "jobs.json"
WORK = Path("art/_work/out/_texturas")
OUT = Path("art/_work/escolha")
ASSETS = Path("src/main/resources/assets/spacehorizons/textures")
MODELS = Path("art/models")
RENDER_CLI = Path.home() / "AppData/Local/blockbench-mcp-headless/render/cli.mjs"
LETTERS = "ABCDEFGH"


def asset_rel(job):
    if "asset" in job:
        return job["asset"]
    return ("block/" if job["kind"] == "block" else "item/") + job["out"] + ".png"


def section(job):
    if "asset" in job:
        return "equipment"
    return "block" if job["kind"] == "block" else "item"


def collect(job):
    """[(letra, caminho, origem)] das variantes geradas desta textura."""
    name = job["out"]
    found = []
    if job["kind"] == "recolor":
        srcs = [(WORK / name / "variantes" / f"{i}.png", f"recolor {job['cor']} #{i}") for i in (1, 2, 3)]
        if "cor_alt" in job:
            srcs += [(WORK / f"{name}_alt" / "variantes" / f"{i}.png", f"recolor {job['cor_alt']} #{i}") for i in (1, 2, 3)]
    else:
        srcs = [(WORK / name / "variantes" / f"{i}.png", f"bruto {i}, voto") for i in range(1, 9)]
        srcs = [s for s in srcs if s[0].exists()]
        if job["kind"] == "block":
            for i in range(1, 9):
                raw = WORK / name / "raw" / f"raw_{i}.png"
                if raw.exists():
                    dst = WORK / name / f"area_{i}.png"
                    subprocess.run([sys.executable, str(HERE / "reduce16.py"), str(raw), str(dst)], check=True)
                    srcs.append((dst, f"bruto {i}, média de área"))
    for letter, (src, origin) in zip(LETTERS, [s for s in srcs if s[0].exists()]):
        found.append((letter, src, origin))
    return found


def models_using():
    """textura -> (modelo, json do modelo) do primeiro modelo que a usa (preferindo o de mesmo prefixo)."""
    uses = {}
    for f in sorted(MODELS.glob("*.bbmodel")):
        d = json.loads(f.read_text(encoding="utf-8"))
        for t in d.get("textures", []):
            tex = Path(t.get("name", "")).stem
            prev = uses.get(tex)
            if prev is None or (tex.startswith(f.stem) and not tex.startswith(prev[0].stem)):
                uses[tex] = (f, d)
    return uses


def render(model_path, model, tex, img_path, out_png):
    d = json.loads(json.dumps(model))
    data = base64.b64encode(Path(img_path).read_bytes()).decode()
    for t in d["textures"]:
        if Path(t.get("name", "")).stem == tex:
            t["source"] = "data:image/png;base64," + data
    tmp = out_png.with_suffix(".bbmodel")
    tmp.write_text(json.dumps(d), encoding="utf-8")
    r = subprocess.run(["node", str(RENDER_CLI), str(tmp), "-o", str(out_png), "--width", "256", "--height", "256",
                        "--view", "three-quarter", "-q"], capture_output=True, text=True)
    tmp.unlink()
    if r.returncode != 0 or not out_png.exists():
        print(f"render falhou: {model_path.name} {tex} {out_png.name}: {(r.stdout + r.stderr).strip()[-300:]}")
        return False
    return True


CSS = """
:root { --bg:#1d1f24; --card:#272a31; --line:#3a3e47; --fg:#e6e8ec; --mute:#9aa1ad; --acc:#f0a63a; }
* { box-sizing:border-box; }
body { margin:0; background:var(--bg); color:var(--fg); font:14px/1.45 system-ui, sans-serif; }
header { padding:20px 24px; border-bottom:1px solid var(--line); }
header h1 { margin:0 0 6px; font-size:20px; }
header p { margin:4px 0; color:var(--mute); max-width:900px; }
code { background:#111317; padding:1px 5px; border-radius:4px; color:var(--acc); }
nav { padding:10px 24px; display:flex; flex-wrap:wrap; gap:6px 12px; border-bottom:1px solid var(--line); }
nav a { color:var(--mute); text-decoration:none; font-size:12px; }
nav a:hover { color:var(--fg); }
section.tex { padding:18px 24px 26px; border-bottom:1px solid var(--line); }
section.tex h2 { margin:0 0 2px; font-size:17px; }
section.tex .meta { color:var(--mute); font-size:12px; margin-bottom:12px; }
.row { display:flex; flex-wrap:wrap; gap:14px; }
.var { background:var(--card); border:1px solid var(--line); border-radius:8px; padding:10px; width:min-content; }
.var.atual { border-color:var(--acc); }
.var h3 { margin:0 0 8px; font-size:15px; display:flex; justify-content:space-between; gap:8px; white-space:nowrap; }
.var h3 small { color:var(--mute); font-weight:400; font-size:11px; }
.pix { image-rendering:pixelated; image-rendering:crisp-edges; display:block; background:
  repeating-conic-gradient(#3a3e47 0% 25%, #2e3139 0% 50%) 0 0/16px 16px; }
.big { width:192px; height:192px; }
.wide { width:512px; height:256px; }
.tile { width:192px; height:192px; margin-top:8px; background-repeat:repeat; background-size:64px 64px;
  image-rendering:pixelated; }
.render { width:192px; height:192px; margin-top:8px; display:block; border-radius:4px; }
.lbl { color:var(--mute); font-size:11px; margin-top:4px; }
"""


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    do_render = "--sem-render" not in sys.argv and RENDER_CLI.exists()
    jobs = json.loads(JOBS.read_text(encoding="utf-8"))["jobs"]
    if (OUT / "var").exists():
        shutil.rmtree(OUT / "var")
    if (OUT / "render").exists():
        shutil.rmtree(OUT / "render")
    uses = models_using() if do_render else {}
    picks = {"_doc": "Troque \"atual\" pela letra da variante escolhida (veja index.html). "
                     "Aplicar: python art/textures/apply_picks.py art/_work/escolha/escolhas_modelo.json",
             "block": {}, "item": {}, "equipment": {}}
    sections, nav, missing, total = [], [], [], 0
    for job in jobs:
        name = job["out"]
        sec = section(job)
        vdir = OUT / "var" / name
        vdir.mkdir(parents=True, exist_ok=True)
        variants = []
        for letter, src, origin in collect(job):
            shutil.copyfile(src, vdir / f"{letter}.png")
            variants.append((letter, origin))
        if not variants:
            missing.append(name)
        total += len(variants)
        cur = ASSETS / asset_rel(job)
        if cur.exists():
            shutil.copyfile(cur, vdir / "atual.png")
            variants.append(("atual", "textura atual em assets/"))
        picks[sec][name] = "atual"
        is_wide = sec == "equipment"
        tiled = job["kind"] == "block"
        model = uses.get(name)
        cards = []
        for letter, origin in variants:
            img = f"var/{name}/{letter}.png"
            parts = [f'<h3>{html.escape(letter)} <small>{html.escape(origin)}</small></h3>',
                     f'<img class="pix {"wide" if is_wide else "big"}" src="{img}" alt="{name} {letter}">']
            if not is_wide:
                parts.append(f'<div class="tile pix" style="background-image:url({img})"></div>'
                             f'<div class="lbl">mosaico 3x3</div>')
            if model:
                rdir = OUT / "render" / name
                rdir.mkdir(parents=True, exist_ok=True)
                rpng = rdir / f"{letter}.png"
                if render(model[0], model[1], name, vdir / f"{letter}.png", rpng):
                    parts.append(f'<img class="render" src="render/{name}/{letter}.png" alt="render">'
                                 f'<div class="lbl">modelo {model[0].stem}</div>')
            cards.append(f'<div class="var{" atual" if letter == "atual" else ""}">{"".join(parts)}</div>')
        letters = ", ".join(l for l, _ in variants)
        title = job.get("nome") or name
        dest = asset_rel(job)
        sections.append(f'<section class="tex" id="{name}"><h2>{html.escape(name)}: {letters}</h2>'
                        f'<div class="meta">{html.escape(title)} · {html.escape(dest)} · seção "{sec}" do JSON</div>'
                        f'<div class="row">{"".join(cards)}</div></section>')
        nav.append(f'<a href="#{name}">{name}</a>')
        print(name, letters, flush=True)
    page = (f'<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">'
            f'<meta name="viewport" content="width=device-width, initial-scale=1">'
            f'<title>Escolha de Texturas</title><style>{CSS}</style></head><body>'
            f'<header><h1>SpaceHorizons: escolha das texturas</h1>'
            f'<p>{len(jobs)} texturas, {total} variantes novas (+ a atual de cada uma). Gerador: A-D = variantes '
            f'do texture.py (redução por voto); blocos, E-H = os mesmos brutos reduzidos por média de área. '
            f'Recolor: A-C e D-F = duas cores, hue shifting suave/médio/forte. Moldura laranja = atual.</p>'
            f'<p>Para escolher, troque <code>"atual"</code> pela letra em <code>art/_work/escolha/escolhas_modelo.json</code> '
            f'e rode <code>python art/textures/apply_picks.py art/_work/escolha/escolhas_modelo.json</code>.</p>'
            f'</header><nav>{"".join(nav)}</nav>{"".join(sections)}</body></html>')
    (OUT / "index.html").write_text(page, encoding="utf-8")
    (OUT / "escolhas_modelo.json").write_text(json.dumps(picks, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(OUT / "index.html")
    if missing:
        print("sem variantes:", ", ".join(missing))


if __name__ == "__main__":
    main()
