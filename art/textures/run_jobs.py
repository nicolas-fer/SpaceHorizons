"""Roda os jobs de textura (texture.py da skill minecraft-entity) em paralelo.

Uso (da raiz do repo):  python art/textures/run_jobs.py art/textures/jobs.json [nomes...] [--variants N]
Os arquivos de trabalho (raw/variantes/comparação) ficam em art/_work/out/_texturas/<nome>/ (fora do git).
Jobs de recolor com "cor_alt" rodam uma segunda vez com essa cor, em art/_work/out/_texturas/<nome>_alt/
(o recolor sempre dá 3 variantes; com a cor alternativa a escolha tem 6).
"""
import json
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

T = Path.home() / ".claude/skills/minecraft-entity/scripts/texture.py"
OUT = Path("art/_work/out")
VARIANTS = "4"


def run(j):
    args = [sys.executable, str(T), j["kind"], "--out", str(OUT / (j["out"] + ".png"))]
    if j["kind"] != "recolor":
        args += ["--variants", VARIANTS]
    for k in ("nome", "descricao", "detalhes", "cores", "orientacao", "base", "cor", "rampa"):
        if k in j:
            args += ["--" + k, j[k]]
    if "ref" in j:
        args += ["--ref", *j["ref"]]
    r = subprocess.run(args, capture_output=True, text=True, encoding="utf-8")
    log = (r.stdout + r.stderr)[-500:]
    if j["kind"] == "recolor" and "cor_alt" in j and r.returncode == 0:
        alt = {k: v for k, v in j.items() if k != "cor_alt"}
        alt.update(out=j["out"] + "_alt", cor=j["cor_alt"])
        _, rc, log2 = run(alt)
        return j["out"], rc, log + " || alt: " + log2
    return j["out"], r.returncode, log


def main():
    global VARIANTS
    sys.stdout.reconfigure(encoding="utf-8")
    argv = sys.argv[1:]
    if "--variants" in argv:
        i = argv.index("--variants")
        VARIANTS = argv[i + 1]
        del argv[i:i + 2]
    data = json.loads(Path(argv[0]).read_text(encoding="utf-8"))
    jobs = [j for j in data["jobs"] if len(argv) < 2 or j["out"] in argv[1:]]
    with ThreadPoolExecutor(6) as ex:
        for name, rc, log in ex.map(run, jobs):
            print(name, rc, log.replace("\n", " | "), flush=True)


if __name__ == "__main__":
    main()
