#!/usr/bin/env python3
"""Gera as texturas PROVISÓRIAS 16x16 da refinaria (só com a biblioteca padrão).

Como rodar (da raiz do projeto):
    python tools/gerar_texturas_refinaria.py

Saída em src/main/resources/assets/spacehorizons/textures/block/:
- refinery.png          frente apagada (também é a textura "do id" exigida pelo ContentResourcesTest)
- refinery_on.png       frente acesa (a boca da fornalha brilha)
- refinery_side.png     laterais e traseira
- refinery_top.png      topo e base

Quando houver arte de verdade, é só substituir os PNGs (e apagar este script).
"""
import struct
import zlib
from pathlib import Path

T = 16
RAIZ = Path(__file__).resolve().parent.parent
SAIDA = RAIZ / "src" / "main" / "resources" / "assets" / "spacehorizons" / "textures" / "block"

METAL = (104, 109, 118)
METAL_CLARO = (132, 138, 148)
METAL_ESCURO = (70, 74, 82)
REBITE = (160, 166, 176)
BOCA = (24, 22, 24)
AMBAR = (224, 160, 48)


def png(linhas):
    bruto = b"".join(b"\x00" + b"".join(bytes(p) + b"\xff" for p in linha) for linha in linhas)

    def bloco(tipo, dados):
        corpo = tipo + dados
        return struct.pack(">I", len(dados)) + corpo + struct.pack(">I", zlib.crc32(corpo) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + bloco(b"IHDR", struct.pack(">IIBBBBB", T, T, 8, 6, 0, 0, 0))
            + bloco(b"IDAT", zlib.compress(bruto, 9)) + bloco(b"IEND", b""))


def base():
    """Placa de metal com borda escura de 1 px e um leve brilho no canto superior esquerdo."""
    g = [[METAL for _ in range(T)] for _ in range(T)]
    for i in range(T):
        for borda in (0, T - 1):
            g[borda][i] = METAL_ESCURO
            g[i][borda] = METAL_ESCURO
    for i in range(1, T - 1):
        g[1][i] = METAL_CLARO
        g[i][1] = METAL_CLARO
    for x, y in ((2, 2), (T - 3, 2), (2, T - 3), (T - 3, T - 3)):
        g[y][x] = REBITE
    return g


def lateral():
    g = base()
    # nervuras horizontais
    for x in range(3, T - 3):
        g[6][x] = METAL_ESCURO
        g[9][x] = METAL_ESCURO
    return g


def topo():
    g = base()
    # escotilha circular
    cx = cy = (T - 1) / 2
    for y in range(T):
        for x in range(T):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 4.5 <= d < 5.5:
                g[y][x] = METAL_ESCURO
            elif d < 4.5:
                g[y][x] = METAL_CLARO if (x + y) % 4 else METAL
    return g


def frente(acesa):
    g = base()
    # visor do tanque no alto (âmbar, só decorativo)
    for x in range(4, T - 4):
        g[3][x] = AMBAR
        g[4][x] = (160, 112, 32)
    # boca da fornalha
    for y in range(7, 13):
        for x in range(4, T - 4):
            if acesa:
                # brasa: mais quente no fundo
                quente = (y - 7) / 5.0
                g[y][x] = (int(200 + 55 * quente), int(80 + 120 * quente), int(16 + 20 * quente))
            else:
                g[y][x] = BOCA
    for x in range(3, T - 3):
        g[6][x] = METAL_ESCURO
        g[13][x] = METAL_CLARO
    return g


def escreve(nome, grade):
    SAIDA.mkdir(parents=True, exist_ok=True)
    (SAIDA / nome).write_bytes(png(grade))
    print("gerado", (SAIDA / nome).relative_to(RAIZ))


if __name__ == "__main__":
    escreve("refinery.png", frente(False))
    escreve("refinery_on.png", frente(True))
    escreve("refinery_side.png", lateral())
    escreve("refinery_top.png", topo())
