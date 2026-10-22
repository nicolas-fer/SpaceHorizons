"""Roda os jobs de textura do Tier 2 (art/tier2/jobs.json), um por vez, com pausa entre os que chamam o Gemini.
Reaproveita art/textures/run_jobs.py (texture.py da skill minecraft-entity, 3 variantes por textura).

Uso (raiz do repo):  python art/tier2/run_jobs.py [nomes...] [--pausa S] [--force]
                     python art/tier2/run_jobs.py --so-prompt   # só grava os prompt.txt que faltam (caminho manual)
  - Pula os jobs que já têm variantes (não gasta cota de novo); --force refaz.
  - Para no primeiro erro de cota (429 / RESOURCE_EXHAUSTED / quota) e imprime a mensagem.
  - Recolor (sem gerador) roda sem pausa.
Saída de trabalho em art/_work/tier2/out/_texturas/<nome>/ (fora do git). Cada chamada ao gerador é contada em
art/_work/tier2/chamadas.log (uma linha por job, com o número de variantes pedidas).
"""
import json
import sys
import time
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "textures"))
import run_jobs as base  # noqa: E402

base.OUT = Path("art/_work/tier2/out")
LOG = Path("art/_work/tier2/chamadas.log")
QUOTA = ("429", "RESOURCE_EXHAUSTED", "quota", "Quota")


def done(j):
    d = base.OUT / "_texturas" / j["out"] / "variantes"
    return d.exists() and any(d.glob("*.png"))


def write_prompts(jobs):
    """Caminho manual (sem API): grava o prompt.txt de cada job do gerador ainda sem variantes, para gerar no
    Gemini/ChatGPT à mão e continuar com texture.py ... --from-raw <imagens>."""
    import argparse
    sys.path.insert(0, str(base.T.parent))
    import texture
    for j in jobs:
        if j["kind"] == "recolor" or done(j):
            continue
        a = argparse.Namespace(kind=j["kind"], nome=j["nome"], descricao=j["descricao"], detalhes=j["detalhes"],
                               cores=j["cores"], orientacao=j.get("orientacao", "diagonal"))
        p = base.OUT / "_texturas" / j["out"] / "prompt.txt"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(texture.fill_prompt(a) + (texture.REF_HINT if j.get("ref") else ""), encoding="utf-8")
        print(p, "refs:", " ".join(j.get("ref", [])))


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    argv = sys.argv[1:]
    if "--so-prompt" in argv:
        argv.remove("--so-prompt")
        jobs = json.loads((HERE / "jobs.json").read_text(encoding="utf-8"))["jobs"]
        write_prompts([j for j in jobs if not argv or j["out"] in argv])
        return
    pausa, force = 20.0, "--force" in argv
    argv = [a for a in argv if a != "--force"]
    if "--pausa" in argv:
        i = argv.index("--pausa")
        pausa = float(argv[i + 1])
        del argv[i:i + 2]
    jobs = json.loads((HERE / "jobs.json").read_text(encoding="utf-8"))["jobs"]
    jobs = [j for j in jobs if not argv or j["out"] in argv]
    LOG.parent.mkdir(parents=True, exist_ok=True)
    first_gen = True
    for j in jobs:
        if done(j) and not force:
            print(j["out"], "já feito, pulando", flush=True)
            continue
        gen = j["kind"] != "recolor"
        if gen:
            if not first_gen:
                time.sleep(pausa)
            first_gen = False
        name, rc, log = base.run(j)
        if gen:
            with LOG.open("a", encoding="utf-8") as f:
                f.write(f"{datetime.now().isoformat(timespec='seconds')} {name} variants={base.VARIANTS} rc={rc}\n")
        print(name, rc, log.replace("\n", " | "), flush=True)
        if rc != 0 and any(q in log for q in QUOTA):
            raise SystemExit(f"parado: erro de cota em {name}:\n{log}")
        if rc != 0:
            raise SystemExit(f"parado: erro em {name}:\n{log}")


if __name__ == "__main__":
    main()
