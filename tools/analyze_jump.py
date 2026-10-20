#!/usr/bin/env python3
"""Analisa o CSV do cenário `jump` do autoteste (JumpProbe): run/jump_<rótulo>.csv.

Uso: python tools/analyze_jump.py run/jump_s54_fast.csv [limite_salto=10]

Linhas (ver JumpProbe.java): F = quadro do cliente, K = tick do cliente, P = pacote que chegou ao cliente, S = tick do servidor.
Um "salto" é um quadro em que a distância câmera-foguete (ou jogador-foguete) difere da mediana por mais que o limite (blocos).
"""
import csv
import statistics
import sys
from collections import Counter, defaultdict


def num(s, default=float("nan")):
    try:
        return float(s)
    except ValueError:
        return default


def main():
    path = sys.argv[1]
    limit = float(sys.argv[2]) if len(sys.argv) > 2 else 10.0
    frames, kticks, packets, sticks = [], [], [], []
    with open(path, newline="", encoding="utf-8") as fh:
        for row in csv.reader(fh):
            if not row:
                continue
            kind = row[0]
            if kind == "F":
                frames.append(dict(t=num(row[1]), tick=int(row[2]), partial=num(row[3]), cam=num(row[4]), pr=num(row[5]),
                                   pchunk=int(row[6]), rchunk=int(row[7]), cchunk=int(row[8]), loaded=int(row[9]), screen=row[10],
                                   overlay=row[11], held=int(row[12]), interp=int(row[13]),
                                   rxo=num(row[16]) if len(row) > 19 else float("nan"), rx=num(row[17]) if len(row) > 19 else float("nan"),
                                   pxo=num(row[18]) if len(row) > 19 else float("nan"), px=num(row[19]) if len(row) > 19 else float("nan")))
            elif kind == "K":
                kticks.append(dict(t=num(row[1]), tick=int(row[2]), chunks=int(row[9]), view=int(row[10])))
            elif kind == "P":
                packets.append(dict(t=num(row[1]), tick=int(row[2]), kind=row[3], who=row[4], extra=",".join(row[5:])))
            elif kind == "S":
                sticks.append(dict(t=num(row[1]), tick=int(row[2]), rx=num(row[3]), ry=num(row[4]), rz=num(row[5]), held=int(row[6]), loads=int(row[9]),
                                   fresh=int(row[10]), total=int(row[11]), tracked=int(row[12]), ms=num(row[13]), dim=row[14],
                                   miss=row[15] if len(row) > 15 else "-"))
    withrocket = [f for f in frames if f["cam"] == f["cam"]]
    print(f"{path}: {len(frames)} quadros ({len(withrocket)} com foguete), {len(kticks)} ticks de cliente, {len(sticks)} ticks de servidor, {len(packets)} pacotes")
    if not withrocket:
        return
    dur = (frames[-1]["t"] - frames[0]["t"]) / 1000.0
    print(f"duração {dur:.1f} s, {len(frames) / dur:.1f} quadros/s")
    # linha de base: mediana móvel (61 quadros), porque a atitude do foguete muda a distância normal câmera-foguete ao longo do voo
    def rolling(key):
        vals = [f[key] for f in withrocket]
        half = 30
        for i, f in enumerate(withrocket):
            f["base_" + key] = statistics.median(vals[max(0, i - half):i + half + 1])
    rolling("cam")
    rolling("pr")
    print(f"mediana global câmera-foguete {statistics.median(f['cam'] for f in withrocket):.2f}, jogador-foguete {statistics.median(f['pr'] for f in withrocket):.2f}; "
          f"máximos {max(f['cam'] for f in withrocket):.1f} / {max(f['pr'] for f in withrocket):.1f}")

    # ticks em que o servidor segurou o passo e ticks de cliente com pacote de posição do foguete
    held_ticks = {s["tick"] for s in sticks if s["held"] == 1}
    rocket_pos_ticks = {p["tick"] for p in packets if p["who"] == "rocket" and p["kind"] in ("teleport", "possync", "move")}
    ticks = [k["tick"] for k in kticks]
    gap_ticks = {t for t in ticks if t not in rocket_pos_ticks}
    # pacotes de posição do foguete processados por tick de cliente: 0 = buraco, >=2 = rajada (dois passos do servidor num tick só, o foguete anda 2 passos)
    pos_per_tick = Counter(p["tick"] for p in packets if p["who"] == "rocket" and p["kind"] in ("teleport", "possync", "move"))
    burst_ticks = {t for t in ticks if pos_per_tick.get(t, 0) >= 2}
    # duração real de cada tick de cliente (ms entre dois K): tick atrasado = rajada de pacotes e tudo anda de uma vez
    kdt = {}
    for a, b in zip(kticks, kticks[1:]):
        kdt[b["tick"]] = b["t"] - a["t"]
    late_ticks = {t for t, d in kdt.items() if d > 60.0}

    def terrain_flags(f):
        flags = []
        if f["pchunk"] == 0:
            flags.append("chunk_jogador")
        if f["rchunk"] == 0:
            flags.append("chunk_foguete")
        if f["cchunk"] == 0:
            flags.append("chunk_camera")
        if f["loaded"] == 0:
            flags.append("cliente_nao_carregado")
        if f["screen"] != "-":
            flags.append("tela:" + f["screen"])
        return flags

    def sync_flags(f):
        flags = []
        if f["held"] == 1:
            flags.append("held_cliente")
        if f["tick"] in held_ticks or (f["tick"] - 1) in held_ticks or (f["tick"] - 2) in held_ticks:
            flags.append("servidor_segurou")
        if f["tick"] in gap_ticks:
            flags.append("sem_pacote_do_foguete_no_tick")
        if f["tick"] in burst_ticks or (f["tick"] - 1) in burst_ticks:
            flags.append("rajada_de_pacotes")
        if f["tick"] in late_ticks or (f["tick"] - 1) in late_ticks:
            flags.append("tick_de_cliente_atrasado")
        return flags

    n = len(withrocket)
    jumps = [f for f in withrocket if abs(f["cam"] - f["base_cam"]) > limit or abs(f["pr"] - f["base_pr"]) > limit]
    print(f"\nSALTOS (|dist - mediana| > {limit:g}): {len(jumps)} de {n} quadros = {100.0 * len(jumps) / n:.1f}%")
    a_only = b_only = both = other = 0
    reasons = Counter()
    for f in jumps:
        a, b = terrain_flags(f), sync_flags(f)
        for r in a + b:
            reasons[r] += 1
        if a and b:
            both += 1
        elif a:
            a_only += 1
        elif b:
            b_only += 1
        else:
            other += 1
    if jumps:
        for label, c in (("só terreno (a)", a_only), ("só sincronia/ticks segurados (b)", b_only), ("ambos", both), ("outros", other)):
            print(f"  {label}: {c} ({100.0 * c / len(jumps):.0f}% dos saltos)")
        print("  motivos (um salto pode ter vários):", dict(reasons))

    if jumps and jumps[0]["rx"] == jumps[0]["rx"]:
        print("  exemplos (xo/x do foguete e do jogador no quadro do salto; o foguete andando para TRÁS ou fora do lugar do jogador é o 'vai e volta'):")
        for f in jumps[:6]:
            print(f"    tick {f['tick']} partial {f['partial']:.2f}: foguete xo={f['rxo']:.1f} x={f['rx']:.1f} (passo {f['rx'] - f['rxo']:+.1f}); "
                  f"jogador xo={f['pxo']:.1f} x={f['px']:.1f} (passo {f['px'] - f['pxo']:+.1f}); held={f['held']} interp={f['interp']}")
        back = [f for f in withrocket if f["rx"] == f["rx"] and f["rx"] - f["rxo"] < -1.0]
        print(f"  quadros com o foguete andando para trás (x < xo): {len(back)} de {n}")
        mism = [f for f in withrocket if f["rx"] == f["rx"] and abs((f["rx"] - f["rxo"]) - (f["px"] - f["pxo"])) > limit]
        print(f"  quadros em que o passo do tick do foguete e o do jogador diferem em mais de {limit:g}: {len(mism)} de {n}")

    # frames com falta de terreno, independentemente de haver salto
    no_terrain = [f for f in frames if terrain_flags(f)]
    print(f"\nQUADROS com falta de terreno no cliente: {len(no_terrain)} de {len(frames)} = {100.0 * len(no_terrain) / len(frames):.1f}%")
    tc = Counter(r for f in no_terrain for r in terrain_flags(f))
    print("  ", dict(tc))
    # blocos de quadros seguidos sem terreno (duração)
    blocks, cur = [], 0
    for f in frames:
        if terrain_flags(f):
            cur += 1
        elif cur:
            blocks.append(cur)
            cur = 0
    if cur:
        blocks.append(cur)
    if blocks:
        print(f"   {len(blocks)} blocos, tamanho médio {statistics.mean(blocks):.1f} quadros, máximo {max(blocks)}")

    # servidor
    print(f"\nSERVIDOR: {len(sticks)} ticks; segurou {len(held_ticks)}; ticks >50 ms: {sum(1 for s in sticks if s['ms'] > 50)}, >100 ms: {sum(1 for s in sticks if s['ms'] > 100)}; "
          f"ms médio {statistics.mean(s['ms'] for s in sticks):.1f}, máx {max(s['ms'] for s in sticks):.1f}")
    fresh = sum(s["fresh"] for s in sticks)
    loads = sum(s["loads"] for s in sticks)
    print(f"  chunks do espaço carregados (eventos) {loads}, novos (gerados) {fresh}, por tick {loads / max(1, len(sticks)):.2f}; "
          f"chunks do jogador não enviados: {sum(1 for s in sticks if s['tracked'] == 0)} ticks")
    # o que faltava carregar à frente (passo: faltando x pedidos), nos ticks segurados e nos que andaram
    def parse_miss(m):
        out = {}
        if m and m != "-":
            for part in m.split("/"):
                step, rest = part.split(":")
                miss, asked = rest.split("x")
                out[int(step)] = (int(miss), int(asked))
        return out
    if sticks and sticks[0].get("miss", "-") != "-":
        for label, group in (("segurados", [s for s in sticks if s["held"] == 1]), ("que andaram", [s for s in sticks if s["held"] == 0])):
            if not group:
                continue
            parsed = [parse_miss(s["miss"]) for s in group]
            summary = {}
            for step in (0, 1, 2, 4, 8, 16, 32):
                have = [p[step] for p in parsed if step in p]
                if have:
                    summary[step] = f"{statistics.mean(m for m, _ in have):.1f}/{statistics.mean(a for _, a in have):.1f}"
            print(f"  chunks que faltam (média por passo à frente, faltando/pedidos) nos ticks {label} ({len(group)}): {summary}")
    # velocidade efetiva do foguete no servidor (horizontal na dimensão de espaço, blocos/tick) e quanto do tempo ficou segurado
    sp = []
    for a, b in zip(sticks, sticks[1:]):
        if b["tick"] == a["tick"] + 1 and a["rx"] == a["rx"] and b["rx"] == b["rx"]:
            sp.append((b["tick"], ((b["rx"] - a["rx"]) ** 2 + (b["rz"] - a["rz"]) ** 2) ** 0.5, b["held"]))
    if sp:
        moving = [v for _, v, h in sp if h == 0]
        print(f"  segurado em {sum(1 for _, _, h in sp if h) } de {len(sp)} ticks ({100.0 * sum(1 for _, _, h in sp if h) / len(sp):.0f}%); "
              f"velocidade média por tick (todos) {statistics.mean(v for _, v, _ in sp):.1f}, quando anda {statistics.mean(moving) if moving else 0:.1f}")
        bins = defaultdict(lambda: [0, 0])
        for _, v, h in sp:
            k = int(v // 20) * 20
            bins[k][0] += 1
            bins[k][1] += h
        print("  segurado por faixa de velocidade do tick (b/tick):", {f"{k}-{k + 20}": f"{h}/{n}" for k, (n, h) in sorted(bins.items())})
    # pacotes
    pk = Counter((p["kind"], p["who"]) for p in packets)
    print("\nPACOTES (tipo, quem):", dict(pk))
    per_tick = defaultdict(int)
    for p in packets:
        if p["kind"] == "chunk":
            per_tick[p["tick"]] += 1
    if per_tick:
        print(f"  chunks por tick de cliente: média {statistics.mean(per_tick.values()):.2f}, máx {max(per_tick.values())}; ticks sem chunk: "
              f"{sum(1 for t in ticks if t not in per_tick)} de {len(ticks)}")
    print(f"  ticks de cliente sem pacote de posição do foguete: {len(gap_ticks)} de {len(ticks)}; com 2 ou mais (rajada): {len(burst_ticks)}; "
          f"ticks de cliente com mais de 60 ms: {len(late_ticks)} (máx {max(kdt.values()) if kdt else 0:.0f} ms)")
    if kticks:
        print(f"  chunks no cliente: mín {min(k['chunks'] for k in kticks)}, máx {max(k['chunks'] for k in kticks)}; distância de visão do cliente: {sorted({k['view'] for k in kticks})}")


if __name__ == "__main__":
    main()
