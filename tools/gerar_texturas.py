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
    MAQUINAS = {"oxygen_compressor": ("compressor", "aco")}  # -> textures/block/<id>{,_on,_side,_top}.png

Modelos de item (MODELOS_ITEM): lingote, pepita, placa, po (pó/mistura; aceita pontos=cor para
salpicos, ex.: carvão na mistura de aço), circuito (placa verde com trilhas douradas), martelo
(cabeça do material + cabo de madeira), galao (galão com bico; o material é a cor do líquido/lata),
tanque e tanque_grande (cilindro de gás com válvula e faixas; o material é a cor do corpo).
Modelos de bloco (MODELOS_BLOCO): bloco_metalico (borda, brilho, rebites), generico (xadrez).
Máquina (MAQUINAS): gera 4 texturas por id (frente desligada = <id>.png, frente ligada = <id>_on.png,
lateral e topo); o blockstate escolhe a frente pelo estado ligado/desligado (ex.: propriedade LIT).
Modelos de máquina (MODELOS_MAQUINA): fornalha (janela com brasa; é o padrão se a entrada for só o
material) e compressor (janela redonda com hélice, ar azulado quando ligada).
Peças de armadura (também em ITENS): capacete, peitoral, calcas e botas desenham o ícone 16x16.
CAMADAS_ARMADURA: id do equipment asset (assets/<mod>/equipment/<id>.json) -> material; gera as
camadas vestidas 64x32 em textures/entity/equipment/humanoid/<id>.png (capacete, peitoral, braços
e botas) e humanoid_leggings/<id>.png (calças e cinto), no layout de UV da armadura do jogo.
As cores vêm de MATERIAIS (aço = cinza azulado; traje = branco gelo). Um id fora das tabelas não
é gerado.
Ids com textura própria (pintada à mão): não os coloque nas tabelas; o script sobrescreve o
que gera.

Saída: src/main/resources/assets/spacehorizons/textures/{item,block,entity/equipment}/
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
    "oxigenio": (96, 168, 232),     # azul claro (tanque de oxigênio pequeno)
    "oxigenio_grande": (62, 128, 206),  # azul mais fundo (tanque de oxigênio grande)
    "traje": (228, 232, 240),       # branco gelo do traje espacial
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
    "fuel_canister": ("galao", "combustivel"),
    "oxygen_tank_small": ("tanque", "oxigenio"),
    "oxygen_tank_large": ("tanque_grande", "oxigenio_grande"),
    "space_helmet": ("capacete", "traje"),
    "space_chestplate": ("peitoral", "traje"),
    "space_leggings": ("calcas", "traje"),
    "space_boots": ("botas", "traje"),
}
BLOCOS = {
    "steel_block": ("bloco_metalico", "aco"),
}
# id da máquina -> (modelo, material); só o material (str) vale como modelo "fornalha". A refinaria não está aqui
# (tem o próprio script, tools/gerar_texturas_refinaria.py).
MAQUINAS = {
    "oxygen_compressor": ("compressor", "aco"),
}
# id do equipment asset -> material (camadas vestidas 64x32 da armadura)
CAMADAS_ARMADURA = {
    "space_suit": "traje",
}


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


def m_tanque(base, **_):
    """Cilindro de gás em pé, estreito (tanque pequeno): válvula no topo e uma faixa clara no meio."""
    cores = paleta(base)
    cores["k"] = (52, 54, 60)      # válvula
    cores["w"] = (232, 232, 224)   # faixa
    return arte([
        "................",
        "......oooo......",
        "......okko......",
        ".......kk.......",
        "....oooooooo....",
        "....ohhlbbdo....",
        "....ohlbbbdo....",
        "....owwwwwwo....",
        "....owwwwwwo....",
        "....olbbbbdo....",
        "....olbbbbdo....",
        "....olbbbddo....",
        "....olbbbddo....",
        "....oddddddo....",
        "....oooooooo....",
        "................",
    ], cores)


def m_tanque_grande(base, **_):
    """Cilindro mais largo (tanque grande): válvula no topo e duas faixas claras."""
    cores = paleta(base)
    cores["k"] = (52, 54, 60)      # válvula
    cores["w"] = (232, 232, 224)   # faixas
    return arte([
        "......oooo......",
        "......okko......",
        ".......kk.......",
        "...oooooooooo...",
        "...ohhhllllbo...",
        "...ohllbbbbdo...",
        "...ohlbbbbbdo...",
        "...owwwwwwwwo...",
        "...olbbbbbbdo...",
        "...olbbbbbbdo...",
        "...olbbbbbddo...",
        "...owwwwwwwwo...",
        "...olbbbbbddo...",
        "...oddddddddo...",
        "...oooooooooo...",
        "................",
    ], cores)


# ---- peças do traje: silhueta por faixas (y, x0, x1) + sombreamento automático + detalhes ----
def _cores_traje(base):
    cores = paleta(base)
    cores.update({
        "g": (138, 148, 164),   # painel cinza
        "k": (66, 74, 90),      # cinza escuro (molduras, solas, conectores)
        "v": (46, 96, 148),     # viseira
        "w": (168, 214, 242),   # reflexo da viseira
        "u": (26, 58, 102),     # viseira, parte baixa
        "r": (222, 64, 58),     # LED vermelho
        "e": (96, 230, 126),    # LED verde
        "y": (240, 200, 70),    # fivela
    })
    return cores


def _forma(faixas, cores):
    """Pinta uma silhueta: contorno escuro, luz nas bordas de cima/esquerda e sombra nas de baixo/direita."""
    dentro = {(x, y) for (y, x0, x1) in faixas for x in range(x0, x1 + 1)}
    img = vazia()

    def fora(x, y):
        return (x, y) not in dentro

    def borda(x, y):
        return (x, y) in dentro and (fora(x - 1, y) or fora(x + 1, y) or fora(x, y - 1) or fora(x, y + 1))

    for (x, y) in dentro:
        if borda(x, y):
            tom = "o"
        else:
            luz = borda(x - 1, y) or borda(x, y - 1)
            sombra = borda(x + 1, y) or borda(x, y + 1)
            tom = "b" if luz == sombra else ("h" if luz else "d")
        img[y][x] = tuple(cores[tom]) + (255,)
    return img


def _detalhe(img, cores, x0, y0, x1, y1, tom):
    """Pinta um retângulo (inclusivo) só sobre pixels já opacos que não são o contorno."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if img[y][x][3] != 0 and tuple(img[y][x][:3]) != tuple(cores["o"]):
                img[y][x] = tuple(cores[tom]) + (255,)


def m_capacete(base, **_):
    cores = _cores_traje(base)
    faixas = [(2, 5, 10), (3, 3, 12), (4, 2, 13)] + [(y, 2, 13) for y in range(5, 12)] + [(12, 3, 12), (13, 4, 11)]
    img = _forma(faixas, cores)
    _detalhe(img, cores, 4, 5, 11, 10, "k")     # moldura da viseira
    _detalhe(img, cores, 5, 6, 10, 9, "v")      # vidro
    _detalhe(img, cores, 5, 9, 10, 9, "u")      # vidro, parte de baixo mais escura
    _detalhe(img, cores, 5, 6, 6, 6, "w")       # reflexos
    _detalhe(img, cores, 5, 7, 5, 7, "w")
    _detalhe(img, cores, 4, 12, 11, 12, "g")    # anel do pescoço
    _detalhe(img, cores, 3, 7, 3, 8, "g")       # comunicadores laterais
    _detalhe(img, cores, 12, 7, 12, 8, "g")
    _detalhe(img, cores, 7, 3, 8, 3, "g")       # cume
    return img


def m_peitoral(base, **_):
    cores = _cores_traje(base)
    faixas = [(2, 2, 5), (2, 10, 13), (3, 1, 6), (3, 9, 14)] + [(y, 1, 14) for y in range(4, 10)]
    faixas += [(10, 3, 12), (11, 3, 12), (12, 3, 12), (13, 4, 11)]
    img = _forma(faixas, cores)
    _detalhe(img, cores, 3, 5, 3, 9, "d")       # divisa entre manga e tronco
    _detalhe(img, cores, 12, 5, 12, 9, "d")
    _detalhe(img, cores, 6, 6, 9, 9, "k")       # painel de controle
    _detalhe(img, cores, 7, 7, 8, 8, "g")
    _detalhe(img, cores, 7, 7, 7, 7, "e")
    _detalhe(img, cores, 8, 7, 8, 7, "r")
    _detalhe(img, cores, 1, 8, 2, 9, "g")       # punhos
    _detalhe(img, cores, 13, 8, 14, 9, "g")
    _detalhe(img, cores, 4, 12, 11, 12, "g")    # cintura
    return img


def m_calcas(base, **_):
    cores = _cores_traje(base)
    faixas = [(y, 3, 12) for y in range(2, 7)] + [(y, 3, 6) for y in range(7, 14)] + [(y, 9, 12) for y in range(7, 14)]
    img = _forma(faixas, cores)
    _detalhe(img, cores, 4, 3, 11, 3, "g")      # cinto
    _detalhe(img, cores, 7, 3, 8, 3, "y")       # fivela
    _detalhe(img, cores, 4, 8, 5, 9, "g")       # joelheiras
    _detalhe(img, cores, 10, 8, 11, 9, "g")
    _detalhe(img, cores, 4, 12, 5, 12, "g")     # tornozelos
    _detalhe(img, cores, 10, 12, 11, 12, "g")
    return img


def m_botas(base, **_):
    cores = _cores_traje(base)
    # bota esquerda com a ponta para a esquerda; a direita é o espelho
    esquerda = [(y, 3, 6) for y in range(4, 9)] + [(9, 2, 6), (10, 0, 6), (11, 0, 6), (12, 0, 6)]
    faixas = list(esquerda) + [(y, 15 - x1, 15 - x0) for (y, x0, x1) in esquerda]
    img = _forma(faixas, cores)
    for (x0, x1) in ((4, 5), (10, 11)):
        _detalhe(img, cores, x0, 4, x1, 5, "g")  # canos
    _detalhe(img, cores, 1, 12, 5, 12, "k")      # solas
    _detalhe(img, cores, 10, 12, 14, 12, "k")
    _detalhe(img, cores, 1, 10, 3, 10, "l")      # biqueiras
    _detalhe(img, cores, 12, 10, 14, 10, "l")
    return img


MODELOS_ITEM = {
    "lingote": m_lingote, "pepita": m_pepita, "placa": m_placa, "po": m_po,
    "circuito": m_circuito, "martelo": m_martelo, "galao": m_galao,
    "tanque": m_tanque, "tanque_grande": m_tanque_grande,
    "capacete": m_capacete, "peitoral": m_peitoral, "calcas": m_calcas, "botas": m_botas,
}


# ---------------------------------------------------------------- camadas VESTIDAS da armadura (64x32)
# Layout de UV da armadura humanoide do jogo (cada caixa WxHxD ocupa uma cruz a partir de (u, v)):
#   cabeça 8x8x8 em (0, 0); corpo 8x12x4 em (16, 16); braço 4x12x4 em (40, 16); perna 4x12x4 em (0, 16).
# O braço esquerdo e a perna esquerda espelham os da direita, então um desenho só vale para os dois lados.
def _camada(largura=64, altura=32):
    return [[(0, 0, 0, 0)] * largura for _ in range(altura)]


def _ret(img, x0, y0, x1, y1, cor):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img[y][x] = tuple(cor) + (255,)


def _painel(img, x0, y0, x1, y1, cores, semente):
    """Face de armadura: base clara, luz em cima, sombra embaixo e à direita, granulado leve."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            tom = cores["b"]
            if y == y0:
                tom = cores["h"]
            elif y == y1 or (x == x1 and x1 - x0 >= 3):
                tom = cores["d"]
            elif ruido(x, y, semente) > 93:
                tom = cores["d"]
            img[y][x] = tuple(tom) + (255,)


def camada_humanoide(base):
    """Capacete com viseira, peitoral com painel e mochila, mangas com punho e botas."""
    c = _cores_traje(base)
    img = _camada()
    # cabeça: topo, laterais, frente e costas (a base é o anel do pescoço, escuro)
    _painel(img, 8, 0, 15, 7, c, 1)
    _ret(img, 11, 0, 12, 7, c["g"])
    _ret(img, 16, 0, 23, 7, c["k"])
    for n, (x0, x1) in enumerate(((0, 7), (8, 15), (16, 23), (24, 31))):
        _painel(img, x0, 8, x1, 15, c, 2 + n)
        _ret(img, x0, 15, x1, 15, c["g"])
    _ret(img, 9, 9, 14, 13, c["k"])                  # viseira: moldura
    _ret(img, 10, 10, 13, 12, c["v"])
    _ret(img, 10, 12, 13, 12, c["u"])
    _ret(img, 10, 10, 11, 10, c["w"])
    _ret(img, 10, 11, 10, 11, c["w"])
    _ret(img, 2, 10, 5, 12, c["g"])                  # comunicador na lateral direita
    _ret(img, 3, 11, 4, 11, c["k"])
    _ret(img, 18, 10, 21, 12, c["g"])                # e na esquerda
    _ret(img, 19, 11, 20, 11, c["k"])
    _ret(img, 26, 10, 29, 13, c["k"])                # conector nas costas do capacete
    _ret(img, 27, 11, 28, 12, c["g"])
    # tronco: lado direito, frente, lado esquerdo e costas (linhas 20 a 28; o cinto fica por conta das calças)
    _painel(img, 20, 16, 27, 19, c, 6)          # ombros (topo)
    _ret(img, 28, 16, 35, 19, c["k"])                # base do corpo (não aparece)
    for n, (x0, x1) in enumerate(((16, 19), (20, 27), (28, 31), (32, 39))):
        _painel(img, x0, 20, x1, 28, c, 7 + n)
        _ret(img, x0, 27, x1, 27, c["g"])            # faixa na base do peitoral
    _ret(img, 22, 22, 25, 26, c["k"])                # painel de controle no peito
    _ret(img, 23, 23, 24, 25, c["g"])
    _ret(img, 23, 23, 23, 23, c["e"])
    _ret(img, 24, 23, 24, 23, c["r"])
    _ret(img, 33, 21, 38, 26, c["g"])                # mochila de suporte à vida nas costas
    _ret(img, 34, 22, 37, 25, c["k"])
    _ret(img, 35, 23, 36, 24, c["g"])
    # braços: topo, base, lado de fora, frente, lado de dentro e atrás (a manga cobre o braço todo)
    _painel(img, 44, 16, 47, 19, c, 11)
    _ret(img, 48, 16, 51, 19, c["k"])
    for n, (x0, x1) in enumerate(((40, 43), (44, 47), (48, 51), (52, 55))):
        _painel(img, x0, 20, x1, 31, c, 12 + n)
        _ret(img, x0, 20, x1, 21, c["g"])            # ombreira
        _ret(img, x0, 28, x1, 28, c["k"])            # junta do punho
        _ret(img, x0, 29, x1, 31, c["g"])            # luva
    # botas: parte de baixo das pernas (linhas 26 a 31); a base é a sola
    _ret(img, 8, 16, 11, 19, c["k"])
    for n, (x0, x1) in enumerate(((0, 3), (4, 7), (8, 11), (12, 15))):
        _painel(img, x0, 26, x1, 31, c, 20 + n)
        _ret(img, x0, 26, x1, 27, c["b"])            # cano claro
        _ret(img, x0, 28, x1, 30, c["g"])            # bota cinza
        _ret(img, x0, 31, x1, 31, c["k"])            # sola
    _ret(img, 4, 29, 7, 30, c["k"])                  # biqueira
    return img


def camada_calcas(base):
    """Pernas inteiras com joelheiras e tornozelos, e o cinto com fivela na base do corpo."""
    c = _cores_traje(base)
    img = _camada()
    _painel(img, 4, 16, 7, 19, c, 30)           # topo das pernas
    for n, (x0, x1) in enumerate(((0, 3), (4, 7), (8, 11), (12, 15))):
        _painel(img, x0, 20, x1, 31, c, 31 + n)
        _ret(img, x0, 30, x1, 31, c["g"])            # tornozelo
    for (x0, x1) in ((4, 7), (0, 3), (8, 11)):  # joelheiras na frente e nas laterais (as costas ficam lisas)
        _ret(img, x0, 25, x1, 27, c["g"])
        _ret(img, x0, 25, x1, 25, c["h"])
    _ret(img, 28, 16, 35, 19, c["b"])                # base do corpo (entrepernas)
    for n, (x0, x1) in enumerate(((16, 19), (20, 27), (28, 31), (32, 39))):
        _painel(img, x0, 27, x1, 31, c, 40 + n)
        _ret(img, x0, 27, x1, 28, c["g"])            # cinto
    _ret(img, 23, 27, 24, 28, c["y"])                # fivela na frente
    return img


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


def _frente_compressor(base, ligada):
    """Frente do compressor: janela redonda com uma hélice (escura quando desligada, ar azulado quando ligada),
    entradas de ar embaixo e lâmpada indicadora."""
    img = _chapa(base, 6)
    c = paleta(base)
    cx, cy = 7.5, 6.0
    for y in range(TAMANHO):
        for x in range(TAMANHO):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 4.5 <= d < 5.5:                        # aro da janela
                cor = c["o"]
            elif d < 4.5:
                helice = abs(x - cx) < 0.9 or abs(y - cy) < 0.9
                if ligada:
                    cor = (232, 246, 255) if helice else ((150, 214, 255) if (x + y) % 3 else (96, 172, 236))
                else:
                    cor = (62, 66, 74) if helice else (30, 32, 38)
            else:
                continue
            img[y][x] = tuple(cor) + (255,)
    for y in (12, 14):                                # entradas de ar
        for x in range(3, 10):
            img[y][x] = tuple(c["d"]) + (255,)
    img[13][12] = ((90, 255, 110) if ligada else (110, 40, 36)) + (255,)   # lâmpada
    return img


def maquina_compressor_frente(base):
    return _frente_compressor(base, False)


def maquina_compressor_frente_ligada(base):
    return _frente_compressor(base, True)


# modelo de máquina -> (lateral, topo, frente desligada, frente ligada)
MODELOS_MAQUINA = {
    "fornalha": (maquina_lateral, maquina_topo, maquina_frente, maquina_frente_ligada),
    "compressor": (maquina_lateral, maquina_topo, maquina_compressor_frente, maquina_compressor_frente_ligada),
}


# ---------------------------------------------------------------- PNG
def codifica_png(linhas):
    """PNG RGBA 8 bits sem entrelaçamento, escrito à mão com zlib."""
    bruto = b"".join(b"\x00" + b"".join(bytes(p) for p in linha) for linha in linhas)

    def bloco(tipo, dados):
        corpo = tipo + dados
        return struct.pack(">I", len(dados)) + corpo + struct.pack(">I", zlib.crc32(corpo) & 0xFFFFFFFF)

    # as camadas de armadura são 64x32; ícones e blocos, 16x16
    cabecalho = struct.pack(">IIBBBBB", len(linhas[0]), len(linhas), 8, 6, 0, 0, 0)
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
    for id_, entrada in MAQUINAS.items():
        modelo, material = ("fornalha", entrada) if isinstance(entrada, str) else entrada
        gera_maquina(saida / "block", id_, MATERIAIS[material], modelo)
    for id_, material in CAMADAS_ARMADURA.items():
        gera_camadas_armadura(saida / "entity" / "equipment", id_, MATERIAIS[material])


def gera_camadas_armadura(destino, id_, base):
    grava(destino / "humanoid", id_, camada_humanoide(base))
    grava(destino / "humanoid_leggings", id_, camada_calcas(base))


def gera_maquina(destino, id_, base, modelo="fornalha"):
    """Quatro texturas: <id>.png (frente desligada, a "textura do id" que o ContentResourcesTest exige),
    <id>_on.png (frente ligada), <id>_side.png e <id>_top.png (como as da refinaria)."""
    lateral, topo, frente, frente_ligada = MODELOS_MAQUINA[modelo]
    grava(destino, id_ + "_side", lateral(base))
    grava(destino, id_ + "_top", topo(base))
    grava(destino, id_, frente(base))
    grava(destino, id_ + "_on", frente_ligada(base))


def gera_amostras(pasta):
    """Um exemplar de cada modelo (inclui máquina e galão) para conferir os desenhos."""
    pasta = Path(pasta)
    material_do_modelo = {"galao": "combustivel", "circuito": "circuito", "capacete": "traje", "peitoral": "traje",
                          "calcas": "traje", "botas": "traje"}
    for nome, funcao in MODELOS_ITEM.items():
        grava(pasta, "item_" + nome, funcao(MATERIAIS[material_do_modelo.get(nome, "aco")]))
    for nome, funcao in MODELOS_BLOCO.items():
        grava(pasta, "bloco_" + nome, funcao(MATERIAIS["aco"]))
    for modelo in MODELOS_MAQUINA:
        gera_maquina(pasta, "maquina_" + modelo, MATERIAIS["aco"], modelo)
    gera_camadas_armadura(pasta, "armadura", MATERIAIS["traje"])


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "--amostras":
        gera_amostras(sys.argv[2])
    else:
        gera_tudo(SAIDA)
