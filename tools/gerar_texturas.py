#!/usr/bin/env python3
"""Gera texturas PROVISÓRIAS 16x16 (PNG) para itens e blocos do mod, só com a biblioteca padrão.

Como rodar (da raiz do projeto):
    python tools/gerar_texturas.py                 # gera as texturas do mod
    python tools/gerar_texturas.py --amostras DIR  # gera uma amostra de cada modelo em DIR (para ver os desenhos)

COMO MAPEAR id -> MODELO
Edite as tabelas ITENS, BLOCOS e MAQUINAS logo abaixo (ids sem o namespace, iguais aos de
com.spacehorizons.content). Cada entrada diz qual MODELO desenha o id e de qual MATERIAL vem a cor:

    ITENS   = {"steel_ingot": ("lingote", "aco"), ...}     # -> textures/item/<id>.png
    BLOCOS  = {"steel_block": ("bloco_metalico", "aco")}   # -> textures/block/<id>.png
    MAQUINAS = {"refinery": "aco"}                         # -> textures/block/<id>_{side,top,front,front_on}.png

Modelos de item (MODELOS_ITEM): lingote, pepita, placa, po (pó/mistura; aceita pontos=cor para
salpicos, ex.: carvão na mistura de aço), circuito (placa verde com trilhas douradas), martelo
(cabeça do material + cabo de madeira), galao (galão com bico; o material é a cor do líquido/lata).
Modelos de bloco (MODELOS_BLOCO): bloco_metalico (borda, brilho, rebites), generico (xadrez).
Máquina (MAQUINAS): gera 4 texturas por id (lateral, topo, frente desligada, frente ligada); o
blockstate escolhe a frente pelo estado ligado/desligado (ex.: propriedade LIT).
As cores vêm de MATERIAIS (aço = cinza azulado). Um id fora das tabelas não é gerado.
Ids com textura própria (pintada à mão): não os coloque nas tabelas; o script sobrescreve o
que gera.

Saída: src/main/resources/assets/spacehorizons/textures/{item,block}/
"""
import struct
import sys
import zlib
from pathlib import Path

TAMANHO = 16
RAIZ = Path(__file__).resolve().parent.parent
SAIDA = RAIZ / "src" / "main" / "resources" / "assets" / "spacehorizons" / "textures"

# --- cores dos materiais (RGB base) ---
MATERIAIS = {
    "aco": (126, 140, 160),         # cinza azulado
    "cobre": (196, 118, 78),
    "ouro": (240, 200, 70),
    "combustivel": (214, 84, 52),   # vermelho-alaranjado (galão de combustível)
    "mistura": (150, 156, 166),     # pó cinza claro
    "circuito": (46, 140, 78),      # placa verde
}
OURO_TRILHA = (232, 190, 70)
MADEIRA = (150, 108, 62)
CARVAO = (36, 36, 42)

# --- tabelas id -> (modelo, material[, opções]) ---
ITENS = {
    "steel_blend": ("po", "mistura", {"pontos": CARVAO}),
    "steel_ingot": ("lingote", "aco"),
    "steel_nugget": ("pepita", "aco"),
    "steel_plate": ("placa", "aco"),
    "forge_hammer": ("martelo", "aco"),
    "basic_circuit": ("circuito", "circuito"),
}
BLOCOS = {
    "steel_block": ("bloco_metalico", "aco"),
}
# id da máquina -> material. Sem entradas por enquanto (o modelo está pronto para a refinaria).
MAQUINAS = {}


# ---------------------------------------------------------------- utilidades de cor
def clamp(v):
    return max(0, min(255, int(v)))


def escurece(c, f):
    return tuple(clamp(v * f) for v in c)


def clareia(c, f):
    """Aproxima do branco (f entre 0 e 1)."""
    return tuple(clamp(v + (255 - v) * f) for v in c)


def paleta(base):
    """Tons derivados de uma cor base: o contorno, d sombra, b base, l luz, h brilho."""
    return {"o": escurece(base, 0.42), "d": escurece(base, 0.7), "b": base,
            "l": clareia(base, 0.35), "h": clareia(base, 0.65)}


def ruido(x, y, semente=0):
    """Número 0..99 determinístico por posição (para salpicos e granulado)."""
    n = (x * 73856093) ^ (y * 19349663) ^ (semente * 83492791)
    n = (n ^ (n >> 13)) * 1274126177
    return (n ^ (n >> 16)) % 100


# ---------------------------------------------------------------- arte em texto
def arte(linhas, cores):
    """Converte 16 linhas de 16 caracteres em pixels RGBA. '.' é transparente."""
    assert len(linhas) == TAMANHO, "a arte precisa ter 16 linhas, tem %d" % len(linhas)
    saida = []
    for n, linha in enumerate(linhas):
        assert len(linha) == TAMANHO, "linha %d da arte tem %d colunas: %r" % (n, len(linha), linha)
        saida.append([(0, 0, 0, 0) if ch == "." else tuple(cores[ch]) + (255,) for ch in linha])
    return saida


def vazia():
    return [[(0, 0, 0, 0)] * TAMANHO for _ in range(TAMANHO)]


# ---------------------------------------------------------------- modelos de ITEM
def m_lingote(base, **_):
    return arte([
        "................",
        "................",
        "................",
        "....oooooooooo..",
        "...ohhhhhhhhbbo.",
        "..olllllllllbbo.",
        "..oooooooooooo..",
        ".obbbbbbbbbbbdo.",
        ".obbbbbbbbbbddo.",
        ".obbbbbbbbbdddo.",
        ".oddddddddddddo.",
        "..oooooooooooo..",
        "................",
        "................",
        "................",
        "................",
    ], paleta(base))


def m_pepita(base, **_):
    return arte([
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        ".....oooooo.....",
        "....ohhlllbo....",
        "....olbbbbdo....",
        "....obbbbddo....",
        ".....oooooo.....",
        "................",
        "................",
        "................",
        "................",
        "................",
    ], paleta(base))


def m_placa(base, **_):
    return arte([
        "................",
        "................",
        "................",
        "..oooooooooooo..",
        "..ohhhhhhhhhlbo.",
        "..odlbbbbbblddo.",
        "..olbbbbbbbbbdo.",
        "..olbbbbbbbbbdo.",
        "..olbbbbbbbbbdo.",
        "..olbbbbbbbbbdo.",
        "..odlbbbbbblddo.",
        "..oddddddddddoo.",
        "..oooooooooooo..",
        "................",
        "................",
        "................",
    ], paleta(base))


def m_po(base, pontos=None, **_):
    cores = paleta(base)
    img = arte([
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "......oooo......",
        ".....ohhllo.....",
        "....ollllbbo....",
        "...ollllbbbbdo..",
        "..olllbbbbbbddo.",
        "..oooooooooooo..",
        "................",
        "................",
        "................",
    ], cores)
    # granulado sutil do pó; com 'pontos' vira mistura salpicada (ex.: carvão)
    for y in range(TAMANHO):
        for x in range(TAMANHO):
            if img[y][x][3] == 0 or img[y][x][:3] == cores["o"]:
                continue
            r = ruido(x, y, 1)
            if pontos is not None and r < 22:
                img[y][x] = tuple(pontos) + (255,)
            elif r > 90:
                img[y][x] = tuple(cores["d"]) + (255,)
    return img


def m_circuito(base, **_):
    cores = paleta(base)
    cores["t"] = OURO_TRILHA   # trilhas
    cores["p"] = clareia(OURO_TRILHA, 0.35)  # pads
    cores["k"] = (28, 30, 34)  # chip
    cores["c"] = (62, 66, 74)  # pinos do chip
    return arte([
        "................",
        ".oooooooooooooo.",
        ".ollllllllllllo.",
        ".opbbbbbbbbbbpo.",
        ".ottcbkkkkbctto.",
        ".obbtbkkkkbtbbo.",
        ".obbtbkkkkbtbdo.",
        ".ottbbkkkkbbtto.",
        ".obbbbbbbbbbbdo.",
        ".obtttttbbtttbo.",
        ".obbbbbtbbbbbdo.",
        ".optttbtttbttpo.",
        ".obbbbbbbbbbddo.",
        ".opbbbbbbbbbpdo.",
        ".oooooooooooooo.",
        "................",
    ], cores)


def m_martelo(base, **_):
    cores = paleta(base)
    cores["w"] = MADEIRA
    cores["v"] = escurece(MADEIRA, 0.6)
    return arte([
        "................",
        "......oooooooo..",
        "......ohhlllbdo.",
        "......ollbbbbdo.",
        "......obbbbbddo.",
        "......odddddddo.",
        "......oooooooo..",
        ".......owwo.....",
        "......owwvo.....",
        ".....owwvo......",
        "....owwvo.......",
        "...owwvo........",
        "..owwvo.........",
        ".owwvo..........",
        ".ovvo...........",
        "................",
    ], cores)


def m_galao(base, **_):
    cores = paleta(base)
    cores["k"] = (52, 54, 60)      # tampa
    cores["w"] = (232, 232, 224)   # etiqueta
    return arte([
        "................",
        "....oooo........",
        "....okko........",
        "....okko........",
        "..ooooooooooooo.",
        "..ohhhhhhhhhbdo.",
        "..olbbbbbbbbbdo.",
        "..olbwwwwwwbddo.",
        "..olbwwwwwwbddo.",
        "..olbwwwwwwbddo.",
        "..olbbbbbbbbbdo.",
        "..olbbbbbbbbddo.",
        "..odddddddddddo.",
        "..ooooooooooooo.",
        "................",
        "................",
    ], cores)


MODELOS_ITEM = {
    "lingote": m_lingote, "pepita": m_pepita, "placa": m_placa, "po": m_po,
    "circuito": m_circuito, "martelo": m_martelo, "galao": m_galao,
}


# ---------------------------------------------------------------- modelos de BLOCO
def m_bloco_metalico(base):
    """Quadrado cheio: borda escura, bisel claro em cima/esquerda e escuro embaixo/direita,
    rebites nos cantos e uma faixa de brilho na diagonal."""
    c = paleta(base)
    img = vazia()
    for y in range(TAMANHO):
        for x in range(TAMANHO):
            if x in (0, 15) or y in (0, 15):
                cor = c["o"]
            elif x == 1 or y == 1:
                cor = c["h"]
            elif x == 14 or y == 14:
                cor = c["d"]
            else:
                cor = c["b"]
                if (x + y) in (8, 9, 10) and 3 <= x <= 12:   # brilho na diagonal
                    cor = c["l"]
                elif ruido(x, y, 2) > 88:
                    cor = c["d"]
            img[y][x] = tuple(cor) + (255,)
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):   # rebites
        img[y][x] = tuple(c["o"]) + (255,)
        img[y - 1][x - 1] = tuple(c["h"]) + (255,)
    return img


def m_bloco_generico(base):
    """Borda escura de 1 px e miolo em xadrez 2x2 de dois tons."""
    img = vazia()
    for y in range(TAMANHO):
        for x in range(TAMANHO):
            if x in (0, 15) or y in (0, 15):
                cor = escurece(base, 0.6)
            elif ((x // 2) + (y // 2)) % 2 == 0:
                cor = base
            else:
                cor = escurece(base, 0.85)
            img[y][x] = tuple(cor) + (255,)
    return img


MODELOS_BLOCO = {"bloco_metalico": m_bloco_metalico, "generico": m_bloco_generico}


# ---------------------------------------------------------------- modelo de MÁQUINA (4 texturas)
def _chapa(base, semente, topo=False):
    """Chapa metálica: borda escura, bisel e rebites; base das faces da máquina."""
    c = paleta(base)
    img = vazia()
    for y in range(TAMANHO):
        for x in range(TAMANHO):
            if x in (0, 15) or y in (0, 15):
                cor = c["o"]
            elif x == 1 or y == 1:
                cor = c["l"]
            elif x == 14 or y == 14:
                cor = c["d"]
            else:
                cor = c["b"]
                if ruido(x, y, semente) > 90:
                    cor = c["d"]
            img[y][x] = tuple(cor) + (255,)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img[y][x] = tuple(c["o"]) + (255,)
    return img


def maquina_lateral(base):
    img = _chapa(base, 3)
    c = paleta(base)
    for x in range(3, 13):       # junta horizontal no meio, como um painel de duas peças
        img[8][x] = tuple(c["d"]) + (255,)
    return img


def maquina_topo(base):
    img = _chapa(base, 4)
    c = paleta(base)
    for y in range(4, 12):       # grade de ventilação
        for x in range(4, 12):
            if (x + y) % 2 == 0:
                img[y][x] = tuple(c["d"]) + (255,)
    return img


def _frente(base, ligada):
    img = _chapa(base, 5)
    c = paleta(base)
    # janela (câmara) 8x6: escura quando desligada, brasa quando ligada
    for y in range(3, 9):
        for x in range(4, 12):
            borda = x in (4, 11) or y in (3, 8)
            if borda:
                cor = c["o"]
            elif ligada:
                cor = (255, 196, 80) if (x + y) % 3 else (255, 140, 40)
            else:
                cor = (30, 32, 38)
            img[y][x] = tuple(cor) + (255,)
    # entradas de ar embaixo
    for y in (10, 12):
        for x in range(4, 12):
            img[y][x] = tuple(c["d"]) + (255,)
    # lâmpada indicadora
    img[10][12] = ((90, 255, 110) if ligada else (110, 40, 36)) + (255,)
    return img


def maquina_frente(base):
    return _frente(base, False)


def maquina_frente_ligada(base):
    return _frente(base, True)


# ---------------------------------------------------------------- PNG
def codifica_png(linhas):
    """PNG RGBA 8 bits sem entrelaçamento, escrito à mão com zlib."""
    bruto = b"".join(b"\x00" + b"".join(bytes(p) for p in linha) for linha in linhas)

    def bloco(tipo, dados):
        corpo = tipo + dados
        return struct.pack(">I", len(dados)) + corpo + struct.pack(">I", zlib.crc32(corpo) & 0xFFFFFFFF)

    cabecalho = struct.pack(">IIBBBBB", TAMANHO, TAMANHO, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + bloco(b"IHDR", cabecalho)
            + bloco(b"IDAT", zlib.compress(bruto, 9)) + bloco(b"IEND", b""))


def grava(destino, nome, img):
    destino.mkdir(parents=True, exist_ok=True)
    arquivo = destino / (nome + ".png")
    arquivo.write_bytes(codifica_png(img))
    try:
        print("gerado", arquivo.relative_to(RAIZ))
    except ValueError:
        print("gerado", arquivo)


def desenha_item(entrada):
    modelo, material = entrada[0], entrada[1]
    opcoes = entrada[2] if len(entrada) > 2 else {}
    return MODELOS_ITEM[modelo](MATERIAIS[material], **opcoes)


def gera_tudo(saida):
    for id_, entrada in ITENS.items():
        grava(saida / "item", id_, desenha_item(entrada))
    for id_, (modelo, material) in BLOCOS.items():
        grava(saida / "block", id_, MODELOS_BLOCO[modelo](MATERIAIS[material]))
    for id_, material in MAQUINAS.items():
        gera_maquina(saida / "block", id_, MATERIAIS[material])


def gera_maquina(destino, id_, base):
    grava(destino, id_ + "_side", maquina_lateral(base))
    grava(destino, id_ + "_top", maquina_topo(base))
    grava(destino, id_ + "_front", maquina_frente(base))
    grava(destino, id_ + "_front_on", maquina_frente_ligada(base))


def gera_amostras(pasta):
    """Um exemplar de cada modelo (inclui máquina e galão) para conferir os desenhos."""
    pasta = Path(pasta)
    for nome, funcao in MODELOS_ITEM.items():
        base = MATERIAIS["combustivel"] if nome == "galao" else MATERIAIS["circuito"] if nome == "circuito" else MATERIAIS["aco"]
        grava(pasta, "item_" + nome, funcao(base))
    for nome, funcao in MODELOS_BLOCO.items():
        grava(pasta, "bloco_" + nome, funcao(MATERIAIS["aco"]))
    gera_maquina(pasta, "maquina", MATERIAIS["aco"])


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "--amostras":
        gera_amostras(sys.argv[2])
    else:
        gera_tudo(SAIDA)
