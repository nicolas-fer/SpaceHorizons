"""Propostas de modelo de bloco (elements) dos blocos da nave: ship_bridge, ship_impulse_engine, ship_seat e
ship_airlock, duas formas (A e B) cada. ship_window é cubo (cube_all).

Mesmo método de art/models/build_models.py (UV pela posição do elemento, cullface na borda), reaproveitando
as funções de lá. A frente do bloco é a face north (o blockstate gira por "facing", como na refinaria).

Uso (raiz do repo): python art/naves/build_ship_models.py
Saída: art/naves/models/src/<bloco>_<A|B>.json. Depois: importar para art/naves/models/<bloco>_<A|B>.bbmodel
(MCP bbmodel_import_java_block) e renderizar (art/naves/escolha_naves.py).
Coordenadas em pixels (0..16); tudo dentro do bloco.
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "models"))
from build_models import el, model  # noqa: E402

OUT = HERE / "models" / "src"
FULL = [0, 0, 16, 16]


# ---------- ship_bridge ----------
def bridge_tex():
    return {"particle": "ship_bridge_side", "front": "ship_bridge", "side": "ship_bridge_side",
            "top": "ship_bridge_top", "casing": "machine_casing"}


def ship_bridge_A():
    """Console inclinado: base, corpo da mesa, tampo inclinado (teclado) e painel de telas em pé atrás."""
    return model(bridge_tex(), [
        el("base", (0, 0, 0), (16, 1, 16), "casing"),
        el("corpo", (1, 1, 2), (15, 6, 15), "side", skip=("down",)),
        el("apoio", (1, 6, 7), (15, 9, 12), "casing", skip=("down",)),
        el("tampo", (1, 6, 2), (15, 7, 12), {"up": "top", "*": "casing"}, skip=("down",),
           rot={"angle": -22.5, "axis": "x", "origin": [8, 6, 2]}, uv={"up": FULL}),
        el("suporte_tela", (6, 8, 12), (10, 10, 14), "casing", skip=("down",)),
        el("tela", (1, 10, 12), (15, 16, 14), {"north": "front", "*": "casing"}, uv={"north": FULL}),
        el("costas", (1, 8, 14), (15, 10, 15), "casing", skip=("down",)),
    ])


def ship_bridge_B():
    """Estação em pedestal: coluna central e painel grande inclinado com as telas, mais duas telas laterais."""
    return model(bridge_tex(), [
        el("base", (2, 0, 2), (14, 2, 14), "casing"),
        el("coluna", (5, 2, 5), (11, 9, 11), "side", skip=("down", "up")),
        el("caixa", (1, 9, 3), (15, 11, 15), {"up": "top", "*": "side"}),
        el("painel", (1, 11, 4), (15, 13, 14), {"north": "casing", "up": "front", "*": "casing"},
           rot={"angle": -22.5, "axis": "x", "origin": [8, 11, 9]}, uv={"up": FULL}),
        el("tela_esq", (0, 11, 10), (2, 16, 15), {"east": "front", "*": "casing"}, uv={"east": [0, 0, 8, 8]}),
        el("tela_dir", (14, 11, 10), (16, 16, 15), {"west": "front", "*": "casing"}, uv={"west": [8, 0, 16, 8]}),
    ])


# ---------- ship_impulse_engine ----------
def engine_tex():
    return {"particle": "ship_impulse_engine_side", "front": "ship_impulse_engine",
            "side": "ship_impulse_engine_side", "casing": "machine_casing"}


def ship_impulse_engine_A():
    """Bocal único: carcaça e bocal escalonado saindo pela frente (north = escape)."""
    return model(engine_tex(), [
        el("carcaca", (0, 0, 4), (16, 16, 16), {"north": "casing", "*": "side"}),
        el("anel", (1, 1, 2), (15, 15, 4), {"north": "front", "*": "casing"}, skip=("south",)),
        el("bocal", (3, 3, 0), (13, 13, 2), {"north": "front", "*": "casing"}, skip=("south",)),
    ])


def ship_impulse_engine_B():
    """Duas naceles: suporte traseiro e dois bocais retangulares lado a lado, cada um com a textura inteira."""
    return model(engine_tex(), [
        el("suporte", (0, 0, 7), (16, 16, 16), {"north": "casing", "*": "side"}),
        el("nacele_esq", (0.5, 2, 0), (7.5, 14, 7), {"north": "front", "*": "side"}, skip=("south",),
           uv={"north": FULL}),
        el("nacele_dir", (8.5, 2, 0), (15.5, 14, 7), {"north": "front", "*": "side"}, skip=("south",),
           uv={"north": FULL}),
        el("aleta_topo", (2, 14, 2), (14, 15, 7), "casing", skip=("south",)),
    ])


# ---------- ship_seat ----------
def seat_tex():
    return {"particle": "ship_seat_frame", "seat": "ship_seat", "frame": "ship_seat_frame",
            "casing": "machine_casing"}


def ship_seat_A():
    """Cadeira de capitão: pedestal giratório, assento, encosto alto e braços com painel."""
    return model(seat_tex(), [
        el("base", (4, 0, 4), (12, 1, 12), "casing"),
        el("pedestal", (6.5, 1, 6.5), (9.5, 5, 9.5), "frame", skip=("up", "down")),
        el("assento", (2, 5, 2), (14, 8, 13), {"*": "seat", "down": "frame"}),
        el("encosto", (2, 8, 12), (14, 16, 15), {"*": "seat"}, skip=("down",)),
        el("braco_esq", (0, 7, 3), (2, 10, 13), "frame"),
        el("braco_dir", (14, 7, 3), (16, 10, 13), "frame"),
        el("apoio_esq", (0.5, 5, 9), (1.5, 7, 11), "frame", skip=("up", "down")),
        el("apoio_dir", (14.5, 5, 9), (15.5, 7, 11), "frame", skip=("up", "down")),
    ])


def ship_seat_B():
    """Cadeira de piloto: base em caixa, assento, encosto com abas laterais e apoio de cabeça."""
    return model(seat_tex(), [
        el("base", (2, 0, 2), (14, 5, 14), "frame"),
        el("assento", (2, 5, 1), (14, 7, 13), {"*": "seat"}, skip=("down",)),
        el("encosto", (3, 7, 12), (13, 14, 14), {"*": "seat"}, skip=("down",)),
        el("aba_esq", (2, 7, 10), (3, 13, 14), "frame", skip=("down",)),
        el("aba_dir", (13, 7, 10), (14, 13, 14), "frame", skip=("down",)),
        el("cabeca", (5, 14, 12), (11, 16, 14), {"*": "seat"}, skip=("down",)),
        el("console_esq", (0, 5, 4), (2, 8, 10), {"up": "frame", "*": "casing"}, skip=("down",)),
        el("console_dir", (14, 5, 4), (16, 8, 10), {"up": "frame", "*": "casing"}, skip=("down",)),
    ])


# ---------- ship_airlock ----------
def airlock_tex():
    return {"particle": "ship_airlock_side", "front": "ship_airlock", "side": "ship_airlock_side",
            "casing": "machine_casing", "hazard": "hazard_stripes"}


def ship_airlock_A():
    """Porta recuada: batente cheio nas bordas e a folha da porta 2 px para dentro, nas duas faces."""
    return model(airlock_tex(), [
        el("batente_esq", (0, 0, 0), (2, 16, 16), {"north": "front", "south": "front", "*": "side"}),
        el("batente_dir", (14, 0, 0), (16, 16, 16), {"north": "front", "south": "front", "*": "side"}),
        el("batente_topo", (2, 14, 0), (14, 16, 16), {"north": "front", "south": "front", "*": "side"},
           skip=("east", "west")),
        el("batente_base", (2, 0, 0), (14, 2, 16), {"north": "front", "south": "front", "*": "side"},
           skip=("east", "west")),
        el("porta", (2, 2, 2), (14, 14, 14), {"north": "front", "south": "front", "*": "casing"},
           skip=("east", "west", "up", "down")),
    ])


def ship_airlock_B():
    """Escotilha com pistões: chapa da porta mais fina, travessa de alerta no alto e dois pistões na frente."""
    return model(airlock_tex(), [
        el("porta", (0, 0, 3), (16, 16, 13), {"north": "front", "south": "front", "*": "side"}),
        el("travessa", (0, 13, 0), (16, 16, 3), {"north": "hazard", "*": "casing"}, skip=("south",)),
        el("pistao_esq", (0.5, 1, 1), (2.5, 13, 3), "casing", skip=("south", "up")),
        el("pistao_dir", (13.5, 1, 1), (15.5, 13, 3), "casing", skip=("south", "up")),
        el("soleira", (0, 0, 0), (16, 1, 3), "casing", skip=("south",)),
    ])


MODELS = {
    "ship_bridge_A": ship_bridge_A, "ship_bridge_B": ship_bridge_B,
    "ship_impulse_engine_A": ship_impulse_engine_A, "ship_impulse_engine_B": ship_impulse_engine_B,
    "ship_seat_A": ship_seat_A, "ship_seat_B": ship_seat_B,
    "ship_airlock_A": ship_airlock_A, "ship_airlock_B": ship_airlock_B,
}

if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in MODELS.items():
        (OUT / (name + ".json")).write_text(json.dumps(fn(), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(OUT / (name + ".json"))
