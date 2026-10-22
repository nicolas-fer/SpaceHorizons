#!/usr/bin/env python3
"""
Gera a arte PROVISORIA do conteudo do Tier 2 (docs/TIER2_IDS.md), sem desenho novo: texturas existentes do mod (ou do vanilla da versao do
mod, lidas do jar que o ModDevGradle baixou) RECOLORIDAS por processamento simples (troca de matiz/saturacao mantendo o brilho de cada pixel,
entao a forma e o sombreamento ficam os da textura de origem), com os NOMES do contrato. A arte final (skill minecraft-entity, pagina de
escolha) grava os mesmos arquivos por cima, sem mudar codigo.

Gera:
  textures/item/<id>.png         itens (titanio, terras raras, galoes, barra nuclear, bobina)
  textures/block/<id>*.png       blocos (regolito, minerio, bloco de titanio, RTG, tanques, motores e as texturas dos modelos 2x2/3x3)
  models/item/<id>.json          itens simples (item/generated)
  models/block/<id>*.json        blocos simples, o cubo do item dos tanques e os modelos dos motores (copias retexturizadas dos do motor
                                 quimico: rocket_engine.json, rocket_engine_2x2.json, rocket_engine_3x3.json, rocket_engine_part.json)
  blockstates/<id>.json          blocos simples e motores (form x facing, como rocket_engine); os tanques saem de tools/gen_connected_block.py
  items/<id>.json                definicao de item do cliente

E chama tools/gen_connected_block.py para hydrogen_tank e xenon_tank (moldura de 2 px, como o rocket_fuel_tank).

Uso (na raiz do projeto; exige um ./gradlew compileJava feito, para o jar do vanilla existir em build/moddev/artifacts):
  python tools/gen_tier2_placeholders.py            gera tudo o que ainda NAO existe (nao sobrescreve arte final)
  python tools/gen_tier2_placeholders.py --force    regera tudo (apaga a arte que estiver nesses nomes!)
"""
import argparse
import colorsys
import copy
import io
import json
import os
import subprocess
import sys
import zipfile

from PIL import Image

ASSETS = os.path.join("src", "main", "resources", "assets", "spacehorizons")
NS = "spacehorizons"
ARTIFACTS = os.path.join("build", "moddev", "artifacts")

FORCE = False


def vanilla_jar():
    for name in sorted(os.listdir(ARTIFACTS)):
        if name.endswith(".jar") and "sources" not in name:
            path = os.path.join(ARTIFACTS, name)
            with zipfile.ZipFile(path) as z:
                if any(n.startswith("assets/minecraft/textures/") for n in z.namelist()):
                    return path
    sys.exit("sem jar do vanilla com texturas em " + ARTIFACTS + " (rode ./gradlew compileJava uma vez)")


_JAR = None


def load(src):
    """'mod:block/x' (textura do mod) ou 'mc:item/x' (vanilla, do jar)."""
    global _JAR
    kind, path = src.split(":", 1)
    if kind == "mod":
        return Image.open(os.path.join(ASSETS, "textures", path + ".png")).convert("RGBA")
    if _JAR is None:
        _JAR = zipfile.ZipFile(vanilla_jar())
    return Image.open(io.BytesIO(_JAR.read("assets/minecraft/textures/" + path + ".png"))).convert("RGBA")


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4))


def recolor(img, color, keep=0.0):
    """Cada pixel ganha o matiz e a saturacao de `color`; o brilho (V) vira V * V_cor / V_medio, preservando o sombreamento.
    `keep` (0..1) mistura de volta a cor original (para manter detalhes coloridos)."""
    tr, tg, tb = hex_rgb(color)
    th, ts, tv = colorsys.rgb_to_hsv(tr, tg, tb)
    px = img.load()
    w, h = img.size
    values = [colorsys.rgb_to_hsv(*(c / 255.0 for c in px[x, y][:3]))[2] for y in range(h) for x in range(w) if px[x, y][3] > 0]
    mean = sum(values) / len(values) if values else 1.0
    out = img.copy()
    po = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            _, s0, v0 = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            v = max(0.0, min(1.0, v0 * tv / max(mean, 1e-3)))
            s = max(0.0, min(1.0, ts * (0.55 + 0.45 * (1.0 - v0)) + 0.15 * s0))
            nr, ng, nb = colorsys.hsv_to_rgb(th, s, v)
            nr, ng, nb = (nr * (1 - keep) + r / 255.0 * keep, ng * (1 - keep) + g / 255.0 * keep, nb * (1 - keep) + b / 255.0 * keep)
            po[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return out


def ore_on(base, ore_src, spot_color):
    """Os pixels coloridos do minerio vanilla (as pintas: saturacao alta) por cima da base, recoloridos em `spot_color`."""
    ore = load(ore_src)
    out = base.copy()
    po, pr = out.load(), ore.load()
    tr, tg, tb = hex_rgb(spot_color)
    th, ts, tv = colorsys.rgb_to_hsv(tr, tg, tb)
    for y in range(16):
        for x in range(16):
            r, g, b, a = pr[x, y]
            _, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            if a > 0 and s > 0.18:
                nr, ng, nb = colorsys.hsv_to_rgb(th, ts, max(0.0, min(1.0, v * tv / 0.6)))
                po[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), 255)
    return out


def save_png(img, rel):
    path = os.path.join(ASSETS, "textures", rel + ".png")
    if os.path.exists(path) and not FORCE:
        return False
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return True


def write_json(rel, data, always=False):
    path = os.path.join(ASSETS, rel)
    if os.path.exists(path) and not FORCE and not always:
        return False
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n", encoding="utf8") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")
    return True


def item_def(id_, model):
    write_json("items/" + id_ + ".json", {"model": {"type": "minecraft:model", "model": model}})


# ------------------------------------------------------------------ itens

ITEMS = {
    # id: (origem, cor)
    "raw_titanium": ("mc:item/raw_iron", "#B9C3CB"),
    "titanium_ingot": ("mc:item/iron_ingot", "#AEBCCB"),
    "titanium_plate": ("mod:item/steel_plate", "#AEBCCB"),
    "rare_earth_dust": ("mc:item/glowstone_dust", "#93A58C"),
    "helium3_canister": ("mod:item/fuel_canister", "#E6E6AE"),
    "hydrogen_canister": ("mod:item/fuel_canister", "#9FD6EE"),
    "xenon_canister": ("mod:item/fuel_canister", "#9C74D8"),
    "nuclear_fuel_rod": ("mc:item/blaze_rod", "#74DA7E"),
    "superconductor_coil": ("mc:item/copper_ingot", "#5E9FC6"),
}


def gen_items():
    for id_, (src, color) in ITEMS.items():
        save_png(recolor(load(src), color), "item/" + id_)
        write_json("models/item/" + id_ + ".json", {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/" + id_}})
        item_def(id_, NS + ":item/" + id_)


# ------------------------------------------------------------------ blocos simples

def simple_block(id_, model):
    write_json("models/block/" + id_ + ".json", model)
    write_json("blockstates/" + id_ + ".json", {"variants": {"": {"model": NS + ":block/" + id_}}})
    item_def(id_, NS + ":block/" + id_)


def gen_simple_blocks():
    regolith = recolor(load("mc:block/gravel"), "#A65B3C")
    save_png(regolith, "block/mars_regolith")
    simple_block("mars_regolith", {"parent": "minecraft:block/cube_all", "textures": {"all": NS + ":block/mars_regolith"}})

    base = recolor(load("mc:block/gravel"), "#8E4F36")
    save_png(ore_on(base, "mc:block/iron_ore", "#D9DEE3"), "block/mars_titanium_ore")
    simple_block("mars_titanium_ore", {"parent": "minecraft:block/cube_all", "textures": {"all": NS + ":block/mars_titanium_ore"}})

    save_png(recolor(load("mc:block/iron_block"), "#AEBCCB"), "block/titanium_block")
    simple_block("titanium_block", {"parent": "minecraft:block/cube_all", "textures": {"all": NS + ":block/titanium_block"}})

    save_png(recolor(load("mod:block/machine_casing"), "#6C737B"), "block/rtg_generator")
    save_png(recolor(load("mod:block/oxygen_compressor_top"), "#8C939B", keep=0.25), "block/rtg_generator_top")
    simple_block("rtg_generator", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "side": NS + ":block/rtg_generator", "top": NS + ":block/rtg_generator_top", "bottom": NS + ":block/rtg_generator_top",
        "particle": NS + ":block/rtg_generator"}})


# ------------------------------------------------------------------ tanques

TANKS = {"hydrogen_tank": "#9FD6EE", "xenon_tank": "#9C74D8"}


def gen_tanks():
    for id_, color in TANKS.items():
        save_png(recolor(load("mod:block/rocket_fuel_tank"), color), "block/" + id_)
        save_png(recolor(load("mod:block/rocket_fuel_tank_top"), color), "block/" + id_ + "_top")
        save_png(recolor(load("mod:block/rocket_fuel_tank_frame"), color), "block/" + id_ + "_frame")
        save_png(recolor(load("mod:block/rocket_fuel_tank_frame_end"), color), "block/" + id_ + "_frame_end")
        with open(os.path.join(ASSETS, "models", "block", "rocket_fuel_tank.json"), encoding="utf8") as fh:
            model = json.load(fh)
        model["credit"] = "Provisorio: tools/gen_tier2_placeholders.py (rocket_fuel_tank retexturizado)"
        model["textures"]["particle"] = NS + ":block/" + id_
        model["textures"]["side"] = NS + ":block/" + id_
        model["textures"]["top"] = NS + ":block/" + id_ + "_top"
        write_json("models/block/" + id_ + ".json", model)
        item_def(id_, NS + ":block/" + id_)
        state = os.path.join(ASSETS, "blockstates", id_ + ".json")
        if FORCE or not os.path.exists(state):
            subprocess.check_call([sys.executable, os.path.join("tools", "gen_connected_block.py"), "--name", id_,
                                   "--texture", NS + ":block/" + id_ + "_frame", "--texture-end", NS + ":block/" + id_ + "_frame_end",
                                   "--frame", "4", "--render-type", "solid", "--particle", NS + ":block/" + id_])


# ------------------------------------------------------------------ motores

ENGINES = {"nuclear_engine": "#6F9172", "ion_engine": "#4F7FD0", "fusion_engine": "#9A62C8"}


def retextured(model_file, id_suffix_map, credit):
    with open(os.path.join(ASSETS, "models", "block", model_file), encoding="utf8") as fh:
        model = json.load(fh)
    model = copy.deepcopy(model)
    model["credit"] = credit
    for key, value in list(model.get("textures", {}).items()):
        if value in id_suffix_map:
            model["textures"][key] = id_suffix_map[value]
    return model


def gen_engines():
    for id_, color in ENGINES.items():
        credit = "Provisorio: tools/gen_tier2_placeholders.py (modelo do motor quimico retexturizado)"
        mapping = {}
        # 1x1: as tres texturas proprias do motor quimico; casco e faixas continuam as compartilhadas
        for suffix in ("", "_top", "_bottom"):
            save_png(recolor(load("mod:block/rocket_engine" + suffix), color, keep=0.2), "block/" + id_ + suffix)
            mapping[NS + ":block/rocket_engine" + suffix] = NS + ":block/" + id_ + suffix
        write_json("models/block/" + id_ + ".json", retextured("rocket_engine.json", mapping, credit))
        # 2x2x2 e 3x3x3: as texturas dos modelos grandes
        for size in ("2x2", "3x3"):
            m = {}
            for part in ("nozzle", "exit", "plate", "pump"):
                src = "rocket_engine_" + size + "_" + part
                save_png(recolor(load("mod:block/" + src), color, keep=0.2), "block/" + id_ + "_" + size + "_" + part)
                m[NS + ":block/" + src] = NS + ":block/" + id_ + "_" + size + "_" + part
            write_json("models/block/" + id_ + "_" + size + ".json", retextured("rocket_engine_" + size + ".json", m, credit))
        write_json("models/block/" + id_ + "_part.json", {"textures": {"particle": NS + ":block/" + id_}, "elements": []})
        variants = {}
        for i, facing in enumerate(["north", "east", "south", "west"]):
            variants["form=solo,facing=" + facing] = {"model": NS + ":block/" + id_}
            variants["form=part,facing=" + facing] = {"model": NS + ":block/" + id_ + "_part"}
            variants["form=master_3,facing=" + facing] = {"model": NS + ":block/" + id_ + "_3x3"}
            entry = {"model": NS + ":block/" + id_ + "_2x2"}
            if i:
                entry["y"] = 90 * i
            variants["form=master_2,facing=" + facing] = entry
        write_json("blockstates/" + id_ + ".json", {"variants": variants})
        item_def(id_, NS + ":block/" + id_)


def main():
    global FORCE
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    FORCE = ap.parse_args().force
    gen_items()
    gen_simple_blocks()
    gen_tanks()
    gen_engines()
    print("arte provisoria do Tier 2 gerada (sem --force, arquivos existentes ficam como estao)")


if __name__ == "__main__":
    main()
