"""Roda os jobs de textura dos blocos da nave (art/naves/jobs.json), um por vez e com pausa entre eles
(para não estourar o limite da API do Gemini). Reaproveita art/textures/run_jobs.py.

Uso (raiz do repo):  python art/naves/run_jobs.py [nomes...] [--variants N] [--pausa S]
Saída de trabalho em art/_work/naves/out/_texturas/<nome>/ (fora do git).
"""
import json
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "textures"))
import run_jobs as base  # noqa: E402

base.OUT = Path("art/_work/naves/out")


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    argv = sys.argv[1:]
    pausa = 20.0
    if "--variants" in argv:
        i = argv.index("--variants")
        base.VARIANTS = argv[i + 1]
        del argv[i:i + 2]
    if "--pausa" in argv:
        i = argv.index("--pausa")
        pausa = float(argv[i + 1])
        del argv[i:i + 2]
    jobs = json.loads((HERE / "jobs.json").read_text(encoding="utf-8"))["jobs"]
    jobs = [j for j in jobs if not argv or j["out"] in argv]
    for n, j in enumerate(jobs):
        if n:
            time.sleep(pausa)
        name, rc, log = base.run(j)
        print(name, rc, log.replace("\n", " | "), flush=True)


if __name__ == "__main__":
    main()
