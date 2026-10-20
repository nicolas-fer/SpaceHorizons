#!/usr/bin/env python3
"""
Deriva, POR PROCESSAMENTO (sem desenho a mao), as duas texturas-fonte do tanque multibloco que o gerador de texturas conectadas pede:
16x16 com uma moldura de 2 pixels em volta (docs/TEXTURAS_CONECTADAS.md).

A textura do tanque atual (rocket_fuel_tank.png) nao tem moldura propria: a borda dela e o desenho do corpo. Entao:
  - a MOLDURA vem do anel de 2 pixels de fora de machine_casing.png (a chapa metalica que o modelo atual ja usa nos aneis do tanque);
  - o MIOLO (12x12) vem do centro (pixels 2..13) da textura do tanque (lado) ou do topo do tanque (pontas).

Provisorio: a arte final dos tanques com moldura (feita a parte, em art/_work/) substitui estes dois PNGs e basta rodar
  python tools/gen_connected_block.py --name rocket_fuel_tank --texture spacehorizons:block/rocket_fuel_tank_frame \
      --texture-end spacehorizons:block/rocket_fuel_tank_frame_end --frame 2 --render-type solid
sem mudar codigo. Este script e a gerar de novo os provisorios:
  python tools/derive_tank_frame.py
  python tools/gen_connected_block.py ... (acima)

Requer Pillow.
"""
import os

from PIL import Image

ASSETS = os.path.join("src", "main", "resources", "assets", "spacehorizons", "textures", "block")
FRAME = 2


def load(name):
    image = Image.open(os.path.join(ASSETS, name + ".png")).convert("RGBA")
    if image.size != (16, 16):
        raise SystemExit(name + " precisa ser 16x16")
    return image


def derive(frame_source, core_source):
    out = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            inside = FRAME <= x < 16 - FRAME and FRAME <= y < 16 - FRAME
            out.putpixel((x, y), core_source.getpixel((x, y)) if inside else frame_source.getpixel((x, y)))
    return out


def main():
    casing = load("machine_casing")
    side = derive(casing, load("rocket_fuel_tank"))
    end = derive(casing, load("rocket_fuel_tank_top"))
    side.save(os.path.join(ASSETS, "rocket_fuel_tank_frame.png"))
    end.save(os.path.join(ASSETS, "rocket_fuel_tank_frame_end.png"))
    print("rocket_fuel_tank_frame.png e rocket_fuel_tank_frame_end.png derivados de machine_casing + rocket_fuel_tank(_top)")


if __name__ == "__main__":
    main()
