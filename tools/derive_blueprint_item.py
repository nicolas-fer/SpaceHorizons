"""Textura PROVISÓRIA do item rocket_blueprint (planta de montagem), derivada por processamento das texturas vanilla do mapa.

Não é arte final: a arte final entra pela skill minecraft-entity nos mesmos arquivos, sem mudar código.

- rocket_blueprint.png (planta com projeto): filled_map.png do vanilla com as cores trocadas pela luminância para o azul de planta
  (papel azul, traços e contorno claros).
- rocket_blueprint_blank.png (em branco): map.png do vanilla com o papel em cinza-azulado claro.

Uso: python tools/derive_blueprint_item.py <jar do Minecraft (client ou merged)>
"""
import io
import sys
import zipfile
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/spacehorizons/textures/item"


def luminance(r, g, b):
    return 0.299 * r + 0.587 * g + 0.114 * b


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def recolor(img, stops):
    """Troca cada pixel opaco pela cor da rampa `stops` (lista de (luminância 0..255, cor)) na luminância dele."""
    out = Image.new("RGBA", img.size)
    src = img.load()
    dst = out.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b, a = src[x, y]
            if a == 0:
                dst[x, y] = (0, 0, 0, 0)
                continue
            lum = luminance(r, g, b)
            color = stops[-1][1]
            for i in range(len(stops) - 1):
                l0, c0 = stops[i]
                l1, c1 = stops[i + 1]
                if lum <= l1:
                    t = 0.0 if l1 == l0 else max(0.0, min(1.0, (lum - l0) / (l1 - l0)))
                    color = lerp(c0, c1, t)
                    break
            dst[x, y] = (*color, a)
    return out


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    with zipfile.ZipFile(sys.argv[1]) as jar:
        filled = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/item/filled_map.png"))).convert("RGBA")
        blank = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/item/map.png"))).convert("RGBA")
    # planta: os traços e o contorno do mapa (escuros) viram linhas claras, os tons médios e o papel viram o azul da planta
    blueprint = recolor(filled, [(70, (214, 234, 255)), (110, (120, 170, 228)), (150, (40, 88, 168)), (210, (46, 98, 180)), (255, (58, 118, 204))])
    paper = recolor(blank, [(60, (40, 52, 70)), (130, (88, 104, 128)), (220, (196, 210, 226)), (255, (232, 240, 248))])
    OUT.mkdir(parents=True, exist_ok=True)
    blueprint.save(OUT / "rocket_blueprint.png")
    paper.save(OUT / "rocket_blueprint_blank.png")
    print("gravado:", OUT / "rocket_blueprint.png", OUT / "rocket_blueprint_blank.png")


if __name__ == "__main__":
    main()
