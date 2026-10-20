"""Página de escolha dos blocos da nave: art/_work/escolha_naves/index.html (autocontida: imagens em base64)
e art/_work/escolha_naves/escolhas.json (modelo a preencher).

Uso (raiz do repo), depois de art/naves/run_jobs.py e da importação dos .bbmodel:
  python art/naves/escolha_naves.py [--sem-render]

Variantes de cada textura (copiadas para art/_work/escolha_naves/var/<textura>/<letra>.png):
  A-D = as 4 variantes do texture.py (redução por voto); E-H = os mesmos brutos 1-4 reduzidos por média de
  área (art/textures/reduce16.py).
Renders: cada proposta de modelo (art/naves/models/<bloco>_<P>.bbmodel) com a letra L aplicada a todas as
texturas do bloco ao mesmo tempo (motor bb-render do MCP bbmodel), mais uma folha de vistas por proposta.
A escolha é aplicada com art/naves/apply_escolha.py.
"""
import base64
import html
import io
import json
import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
TEXTURES = HERE.parent / "textures"
WORK = Path("art/_work/naves/out/_texturas")
OUT = Path("art/_work/escolha_naves")
MODELS = HERE / "models"
ASSETS = Path("src/main/resources/assets/spacehorizons/textures/block")
RENDER_CLI = Path.home() / "AppData/Local/blockbench-mcp-headless/render/cli.mjs"
LETTERS = "ABCDEFGH"

# bloco -> (texturas próprias, propostas de modelo; [] = cubo)
BLOCKS = {
    "ship_bridge": (["ship_bridge", "ship_bridge_side", "ship_bridge_top"], ["A", "B"]),
    "ship_impulse_engine": (["ship_impulse_engine", "ship_impulse_engine_side"], ["A", "B"]),
    "ship_window": (["ship_window"], []),
    "ship_seat": (["ship_seat", "ship_seat_frame"], ["A", "B"]),
    "ship_airlock": (["ship_airlock", "ship_airlock_side"], ["A", "B"]),
}
MODEL_DOC = {
    "ship_bridge_A": "console inclinado: mesa, tampo inclinado (teclado) e painel de telas em pé",
    "ship_bridge_B": "estação em pedestal: coluna, painel grande inclinado com as telas e duas telas laterais",
    "ship_impulse_engine_A": "bocal único escalonado saindo da carcaça",
    "ship_impulse_engine_B": "duas naceles lado a lado com aleta no topo",
    "ship_seat_A": "cadeira de capitão: pedestal giratório, encosto alto, braços",
    "ship_seat_B": "cadeira de piloto: base em caixa, abas laterais, apoio de cabeça, consoles nos braços",
    "ship_airlock_A": "porta recuada 2 px dentro do batente (as duas faces)",
    "ship_airlock_B": "escotilha mais fina com travessa de alerta e dois pistões na frente",
}


def b64(path):
    return "data:image/png;base64," + base64.b64encode(Path(path).read_bytes()).decode()


def tile3(path):
    im = Image.open(path).convert("RGBA")
    t = Image.new("RGBA", (im.width * 3, im.height * 3))
    for x in range(3):
        for y in range(3):
            t.paste(im, (x * im.width, y * im.height))
    buf = io.BytesIO()
    t.save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def collect(tex):
    srcs = [(WORK / tex / "variantes" / f"{i}.png", f"bruto {i}, voto") for i in range(1, 9)]
    srcs = [s for s in srcs if s[0].exists()]
    for i in range(1, 9):
        raw = WORK / tex / "raw" / f"raw_{i}.png"
        if raw.exists():
            dst = WORK / tex / f"area_{i}.png"
            subprocess.run([sys.executable, str(TEXTURES / "reduce16.py"), str(raw), str(dst)], check=True)
            srcs.append((dst, f"bruto {i}, média de área"))
    return list(zip(LETTERS, srcs))


def render(model, texmap, out_png, view="three-quarter", size=256):
    """texmap: nome da textura -> png. Grava out_png; devolve True se renderizou."""
    d = json.loads(json.dumps(model))
    for t in d["textures"]:
        stem = Path(t.get("name", "")).stem
        if stem in texmap:
            t["source"] = b64(texmap[stem])
    tmp = out_png.with_suffix(".bbmodel")
    tmp.write_text(json.dumps(d), encoding="utf-8")
    r = subprocess.run(["node", str(RENDER_CLI), str(tmp), "-o", str(out_png), "--width", str(size),
                        "--height", str(size), "--view", view, "-q"], capture_output=True, text=True)
    tmp.unlink()
    if r.returncode != 0 or not out_png.exists():
        print(f"render falhou: {out_png}: {(r.stdout + r.stderr).strip()[-300:]}")
        return False
    return True


CSS = """
:root { --bg:#1d1f24; --card:#272a31; --line:#3a3e47; --fg:#e6e8ec; --mute:#9aa1ad; --acc:#f0a63a; }
* { box-sizing:border-box; }
body { margin:0; background:var(--bg); color:var(--fg); font:14px/1.45 system-ui, sans-serif; }
header { padding:20px 24px; border-bottom:1px solid var(--line); }
header h1 { margin:0 0 6px; font-size:20px; }
header p { margin:4px 0; color:var(--mute); max-width:980px; }
code { background:#111317; padding:1px 5px; border-radius:4px; color:var(--acc); }
nav { padding:10px 24px; display:flex; flex-wrap:wrap; gap:6px 14px; border-bottom:1px solid var(--line); }
nav a { color:var(--mute); text-decoration:none; }
nav a:hover { color:var(--fg); }
section.blk { padding:18px 24px 26px; border-bottom:2px solid var(--line); }
section.blk h2 { margin:0 0 4px; font-size:20px; color:var(--acc); }
h3.sub { margin:18px 0 8px; font-size:16px; }
.meta { color:var(--mute); font-size:12px; margin-bottom:10px; }
.row { display:flex; flex-wrap:wrap; gap:12px; }
.var { background:var(--card); border:1px solid var(--line); border-radius:8px; padding:10px; width:min-content; }
.var.atual { border-color:#5a6070; opacity:.85; }
.var h4 { margin:0 0 8px; font-size:15px; display:flex; justify-content:space-between; gap:8px; white-space:nowrap; }
.var h4 small { color:var(--mute); font-weight:400; font-size:11px; }
.pix { image-rendering:pixelated; image-rendering:crisp-edges; display:block; }
.big { width:160px; height:160px; }
.tile { width:160px; height:160px; margin-top:8px; }
.render { width:160px; height:160px; display:block; border-radius:4px; background:#30343c; }
.sheet { width:200px; height:200px; display:block; border-radius:4px; background:#30343c; }
.lbl { color:var(--mute); font-size:11px; margin-top:4px; }
table.grid { border-collapse:separate; border-spacing:8px; }
table.grid th { color:var(--mute); font-weight:500; font-size:13px; text-align:left; vertical-align:middle; }
"""


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    do_render = "--sem-render" not in sys.argv and RENDER_CLI.exists()
    for sub in ("var", "render"):
        if (OUT / sub).exists():
            shutil.rmtree(OUT / sub)
    picks = {"_doc": "Preencha cada campo com a letra escolhida (veja index.html). 'modelo': A/B (proposta de forma) ou "
                     "'cubo'; 'texturas': letra A-H por textura. Campo vazio = não aplica. Aplicar: "
                     "python art/naves/apply_escolha.py art/_work/escolha_naves/escolhas.json"}
    sections, nav, summary = [], [], []
    for block, (texs, props) in BLOCKS.items():
        picks[block] = {"modelo": "cubo" if not props else "", "texturas": {t: "" for t in texs}}
        parts = [f'<section class="blk" id="{block}"><h2>{block}</h2>']
        letters_of = {}
        for tex in texs:
            vdir = OUT / "var" / tex
            vdir.mkdir(parents=True, exist_ok=True)
            cards = []
            found = collect(tex)
            letters_of[tex] = [l for l, _ in found]
            for letter, (src, origin) in found:
                shutil.copyfile(src, vdir / f"{letter}.png")
                p = vdir / f"{letter}.png"
                cards.append(f'<div class="var"><h4>{letter} <small>{html.escape(origin)}</small></h4>'
                             f'<img class="pix big" src="{b64(p)}" alt="{tex} {letter}">'
                             f'<img class="pix tile" src="{tile3(p)}" alt="mosaico"><div class="lbl">mosaico 3x3</div></div>')
            prov = ASSETS / f"{tex}.png"
            if prov.exists():
                cards.append(f'<div class="var atual"><h4>atual <small>provisória em assets/</small></h4>'
                             f'<img class="pix big" src="{b64(prov)}" alt="atual">'
                             f'<img class="pix tile" src="{tile3(prov)}" alt="mosaico"><div class="lbl">mosaico 3x3</div></div>')
            summary.append(f"{tex}: {len(found)}")
            parts.append(f'<h3 class="sub">Textura <code>{tex}</code>: {", ".join(letters_of[tex]) or "sem variantes"}</h3>'
                         f'<div class="meta">assets/spacehorizons/textures/block/{tex}.png · campo '
                         f'"{block}.texturas.{tex}" do JSON</div><div class="row">{"".join(cards)}</div>')
        if props and do_render:
            all_letters = sorted(set().union(*letters_of.values()), key=LETTERS.index)
            rows = []
            for p in props:
                mname = f"{block}_{p}"
                mfile = MODELS / f"{mname}.bbmodel"
                if not mfile.exists():
                    rows.append(f'<tr><th>{p}</th><td>falta {mfile.name}</td></tr>')
                    continue
                model = json.loads(mfile.read_text(encoding="utf-8"))
                rdir = OUT / "render" / mname
                rdir.mkdir(parents=True, exist_ok=True)
                cells = []
                vista_tex = {t: OUT / "var" / t / "A.png" for t in texs if (OUT / "var" / t / "A.png").exists()}
                sheet = []
                for view in ("front", "right", "back", "top"):
                    png = rdir / f"vista_{view}.png"
                    if render(model, vista_tex, png, view=view, size=200):
                        sheet.append(f'<div><img class="sheet" src="{b64(png)}" alt="{view}"><div class="lbl">{view} (A)</div></div>')
                for L in all_letters:
                    texmap = {t: OUT / "var" / t / f"{L}.png" for t in texs if (OUT / "var" / t / f"{L}.png").exists()}
                    png = rdir / f"{L}.png"
                    if render(model, texmap, png):
                        cells.append(f'<td><img class="render" src="{b64(png)}" alt="{mname} {L}"><div class="lbl">'
                                     f'texturas {L}</div></td>')
                rows.append(f'<tr><th>Modelo {p}<br><small>{html.escape(MODEL_DOC.get(mname, ""))}</small></th>'
                            f'{"".join(cells)}</tr>'
                            f'<tr><th></th><td colspan="{len(cells)}"><div class="row">{"".join(sheet)}</div></td></tr>')
            parts.append(f'<h3 class="sub">Modelos (campo "{block}.modelo"): {", ".join(props)}</h3>'
                         f'<div class="meta">Cada render usa a mesma letra em todas as texturas do bloco '
                         f'({", ".join(texs)}); no JSON a letra é escolhida por textura. Fontes: '
                         f'art/naves/models/{block}_&lt;P&gt;.bbmodel</div>'
                         f'<div style="overflow-x:auto"><table class="grid">{"".join(rows)}</table></div>')
        elif not props:
            parts.append('<h3 class="sub">Modelo: cubo (cube_all)</h3><div class="meta">O mosaico 3x3 acima mostra a '
                         'parede de janelas. Vidro conectado (sem moldura entre blocos vizinhos) precisa de código: '
                         'NeoForge não tem connected textures nativo.</div>')
        parts.append("</section>")
        sections.append("".join(parts))
        nav.append(f'<a href="#{block}">{block}</a>')
        print(block, "ok", flush=True)
    page = (f'<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">'
            f'<meta name="viewport" content="width=device-width, initial-scale=1">'
            f'<title>Escolha dos Blocos da Nave</title><style>{CSS}</style></head><body>'
            f'<header><h1>SpaceHorizons: blocos da nave</h1>'
            f'<p>Texturas: A-D = variantes do texture.py (redução por voto); E-H = os mesmos brutos reduzidos por '
            f'média de área. Moldura cinza = textura provisória atual (se houver). Modelos: propostas A e B por bloco '
            f'(a janela é cubo).</p>'
            f'<p>Preencha <code>art/_work/escolha_naves/escolhas.json</code> e rode '
            f'<code>python art/naves/apply_escolha.py art/_work/escolha_naves/escolhas.json</code>.</p>'
            f'</header><nav>{"".join(nav)}</nav>{"".join(sections)}</body></html>')
    (OUT / "index.html").write_text(page, encoding="utf-8")
    (OUT / "escolhas.json").write_text(json.dumps(picks, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(OUT / "index.html")
    print("variantes:", "; ".join(summary))


if __name__ == "__main__":
    main()
