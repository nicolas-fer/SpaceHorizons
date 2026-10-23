#!/usr/bin/env python3
"""
Gera a arte PROVISORIA da base lunar (etapa 4 da sobrevivencia; ids em docs/PLANO_SOBREVIVENCIA.md, "Etapa 4"), sem desenho novo: texturas
existentes do mod (ou do vanilla da versao, lidas do jar do ModDevGradle) RECOLORIDAS por processamento simples, com os NOMES finais. A
arte final (skill minecraft-entity, pagina de escolha) grava os mesmos arquivos por cima, sem mudar codigo.

Reaproveita as funcoes de tools/gen_tier2_placeholders.py (recolor, ore_on, save_png...). Sem --force nao sobrescreve nada que ja exista.

Gera:
  textures/item/lunar_ice.png                       gelo lunar (quartzo do vanilla recolorido)
  textures/block/lunar_ice_ore.png                  minerio (andesito + pintas de gelo)
  textures/block/<id>_top.png, <id>_side.png, <id>_side_on.png   painel, bateria, cabo e as quatro maquinas
  models/block/<id>.json, <id>_on.json, blockstates/<id>.json, items/<id>.json, models/item/lunar_ice.json

Uso (na raiz do projeto; exige um ./gradlew compileJava feito, para o jar do vanilla existir em build/moddev/artifacts):
  python tools/gen_moonbase_placeholders.py            gera o que ainda NAO existe
  python tools/gen_moonbase_placeholders.py --force    regera tudo (apaga a arte que estiver nesses nomes!)
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_tier2_placeholders as t2  # noqa: E402

NS = t2.NS


def grid(img, step=8, factor=0.55):
    """Escurece as linhas de uma grade (celulas do painel solar): processamento, nao desenho."""
    out = img.copy()
    px = out.load()
    for y in range(out.size[1]):
        for x in range(out.size[0]):
            if x % step == 0 or y % step == 0:
                r, g, b, a = px[x, y]
                px[x, y] = (round(r * factor), round(g * factor), round(b * factor), a)
    return out


# id: (textura do lado, textura do lado aceso, textura de cima, cor)
MACHINES = {
    "battery": ("mod:block/machine_casing", None, "mod:block/oxygen_compressor_top", "#5F9A6C"),
    "power_cable": ("mod:block/machine_casing", None, "mod:block/oxygen_compressor_top", "#B8733A"),
    "oxygen_generator": ("mod:block/oxygen_compressor_side", "mod:block/oxygen_compressor_on", "mod:block/oxygen_compressor_top", "#7FB7D8"),
    "helium3_extractor": ("mod:block/refinery_side", "mod:block/refinery_on", "mod:block/refinery_top", "#D6D27A"),
    "electrolyzer": ("mod:block/oxygen_compressor_side", "mod:block/oxygen_compressor_on", "mod:block/oxygen_compressor_top", "#66C9B5"),
    "lunar_refinery": ("mod:block/refinery_side", "mod:block/refinery_on", "mod:block/refinery_top", "#B9BCC9"),
}


def cube_model(id_, side, top):
    return {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "side": NS + ":block/" + side, "top": NS + ":block/" + top, "bottom": NS + ":block/" + top, "particle": NS + ":block/" + side}}


def block_files(id_, lit_model):
    """Blockstate (lit=false e lit=true) e item do bloco."""
    t2.write_json("blockstates/" + id_ + ".json", {"variants": {
        "lit=false": {"model": NS + ":block/" + id_},
        "lit=true": {"model": NS + ":block/" + (id_ + "_on" if lit_model else id_)}}})
    t2.item_def(id_, NS + ":block/" + id_)


def gen():
    # gelo lunar (item) e o minerio
    t2.save_png(t2.recolor(t2.load("mc:item/quartz"), "#BDE6F2"), "item/lunar_ice")
    t2.write_json("models/item/lunar_ice.json", {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/lunar_ice"}})
    t2.item_def("lunar_ice", NS + ":item/lunar_ice")

    ore = t2.ore_on(t2.load("mc:block/andesite"), "mc:block/iron_ore", "#C4EAF7")
    t2.save_png(ore, "block/lunar_ice_ore")
    t2.write_json("models/block/lunar_ice_ore.json", {"parent": "minecraft:block/cube_all", "textures": {"all": NS + ":block/lunar_ice_ore"}})
    t2.write_json("blockstates/lunar_ice_ore.json", {"variants": {"": {"model": NS + ":block/lunar_ice_ore"}}})
    t2.item_def("lunar_ice_ore", NS + ":block/lunar_ice_ore")

    # painel solar: celulas azul-escuras em cima, carcaca de maquina nos lados
    t2.save_png(grid(t2.recolor(t2.load("mc:block/lapis_block"), "#2A4B94")), "block/solar_panel_top")
    t2.save_png(t2.recolor(t2.load("mod:block/machine_casing"), "#6C737B"), "block/solar_panel_side")
    t2.write_json("models/block/solar_panel.json", cube_model("solar_panel", "solar_panel_side", "solar_panel_top"))
    block_files("solar_panel", False)

    for id_, (side, side_on, top, color) in MACHINES.items():
        t2.save_png(t2.recolor(t2.load(side), color), "block/" + id_ + "_side")
        if side_on:
            t2.save_png(t2.recolor(t2.load(side_on), color), "block/" + id_ + "_side_on")
        t2.save_png(t2.recolor(t2.load(top), color, keep=0.2), "block/" + id_ + "_top")
        t2.write_json("models/block/" + id_ + ".json", cube_model(id_, id_ + "_side", id_ + "_top"))
        if side_on:
            t2.write_json("models/block/" + id_ + "_on.json", cube_model(id_, id_ + "_side_on", id_ + "_top"))
        block_files(id_, side_on is not None)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true")
    t2.FORCE = parser.parse_args().force
    gen()
    print("ok")
