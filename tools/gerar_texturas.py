#!/usr/bin/env python3
"""Gera texturas PROVISÓRIAS 16x16 (PNG) para itens e blocos do mod, só com a biblioteca padrão.

Como rodar (da raiz do projeto):
    python tools/gerar_texturas.py

Como adicionar ids: edite as listas ITENS e BLOCOS logo abaixo (ids sem o namespace,
iguais aos usados nos registros de com.spacehorizons.content) e rode o script de novo.

Saída: src/main/resources/assets/spacehorizons/textures/{item,block}/<id>.png
- A cor base vem de um hash do id (o mesmo id sempre gera a mesma cor).
- Itens: losango sobre fundo transparente (parece um "objeto").
- Blocos: quadrado cheio com borda mais escura e miolo xadrez (parece um "bloco").
Texturas já existentes são sobrescritas; se você pintar uma textura de verdade,
tire o id da lista para o script não apagá-la.
"""
import colorsys
import hashlib
import struct
import zlib
from pathlib import Path

# ids com textura provisória (adicione aqui)
ITENS = ["placeholder_item", "fuel_canister"]
BLOCOS = ["placeholder_block"]

TAMANHO = 16
RAIZ = Path(__file__).resolve().parent.parent
SAIDA = RAIZ / "src" / "main" / "resources" / "assets" / "spacehorizons" / "textures"


def cor_do_id(id_):
    """Cor RGB estável derivada do id (matiz do hash, saturação e brilho médios)."""
    h = hashlib.sha256(id_.encode("utf-8")).digest()
    matiz = int.from_bytes(h[:2], "big") / 65535.0
    r, g, b = colorsys.hsv_to_rgb(matiz, 0.55, 0.85)
    return int(r * 255), int(g * 255), int(b * 255)


def escurece(c, f):
    return tuple(int(v * f) for v in c)


def pixels_item(cor):
    """Losango com contorno escuro; fora dele, transparente."""
    linhas = []
    centro = (TAMANHO - 1) / 2
    for y in range(TAMANHO):
        linha = []
        for x in range(TAMANHO):
            d = abs(x - centro) + abs(y - centro)
            if d <= 6.5:
                linha.append(escurece(cor, 0.55) + (255,) if d > 5.5 else cor + (255,))
            else:
                linha.append((0, 0, 0, 0))
        linhas.append(linha)
    return linhas


def pixels_bloco(cor):
    """Quadrado cheio: borda escura de 1 px e miolo em xadrez 2x2 de dois tons."""
    linhas = []
    for y in range(TAMANHO):
        linha = []
        for x in range(TAMANHO):
            if x in (0, TAMANHO - 1) or y in (0, TAMANHO - 1):
                linha.append(escurece(cor, 0.6) + (255,))
            elif ((x // 2) + (y // 2)) % 2 == 0:
                linha.append(cor + (255,))
            else:
                linha.append(escurece(cor, 0.85) + (255,))
        linhas.append(linha)
    return linhas


def codifica_png(linhas):
    """PNG RGBA 8 bits sem entrelaçamento, escrito à mão com zlib."""
    bruto = b"".join(b"\x00" + b"".join(bytes(p) for p in linha) for linha in linhas)

    def bloco(tipo, dados):
        corpo = tipo + dados
        return struct.pack(">I", len(dados)) + corpo + struct.pack(">I", zlib.crc32(corpo) & 0xFFFFFFFF)

    cabecalho = struct.pack(">IIBBBBB", TAMANHO, TAMANHO, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + bloco(b"IHDR", cabecalho)
            + bloco(b"IDAT", zlib.compress(bruto, 9)) + bloco(b"IEND", b""))


def escreve(pasta, ids, funcao):
    destino = SAIDA / pasta
    destino.mkdir(parents=True, exist_ok=True)
    for id_ in ids:
        arquivo = destino / (id_ + ".png")
        arquivo.write_bytes(codifica_png(funcao(cor_do_id(id_))))
        print("gerado", arquivo.relative_to(RAIZ))


if __name__ == "__main__":
    escreve("item", ITENS, pixels_item)
    escreve("block", BLOCOS, pixels_bloco)
