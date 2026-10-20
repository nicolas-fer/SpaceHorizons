"""Roda os jobs de textura (texture.py da skill minecraft-entity) em paralelo.

Uso (da raiz do repo):  python art/textures/run_jobs.py art/textures/jobs.json [nomes...]
Os arquivos de trabalho (raw/variantes/comparação) ficam em art/_work/out/_texturas/<nome>/ (fora do git).
"""
import json
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

T = Path.home() / ".claude/skills/minecraft-entity/scripts/texture.py"
OUT = Path("art/_work/out")


def run(j):
    args = [sys.executable, str(T), j["kind"], "--out", str(OUT / (j["out"] + ".png"))]
    for k in ("nome", "descricao", "detalhes", "cores", "orientacao", "base", "cor", "rampa"):
        if k in j:
            args += ["--" + k, j[k]]
    if "ref" in j:
        args += ["--ref", *j["ref"]]
    r = subprocess.run(args, capture_output=True, text=True, encoding="utf-8")
    return j["out"], r.returncode, (r.stdout + r.stderr)[-500:]


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    data = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    jobs = [j for j in data["jobs"] if len(sys.argv) < 3 or j["out"] in sys.argv[2:]]
    with ThreadPoolExecutor(6) as ex:
        for name, rc, log in ex.map(run, jobs):
            print(name, rc, log.replace("\n", " | "))


if __name__ == "__main__":
    main()
