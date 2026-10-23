"""Arte PROVISÓRIA da trama (etapa 5), derivada por processamento de texturas existentes; ids estáveis, a arte final entra nos mesmos arquivos.

- textures/block/data_terminal.png: a tela da ponte de comando (ship_bridge.png) recolorida para verde de terminal.
- models/block/data_terminal.json: o modelo da ponte de comando com a textura da tela trocada.
- blockstates/data_terminal.json: 4 direções x 3 registros (aurora, selene, vigia), todos com o mesmo modelo.
- textures/item/journal_entry.png: written_book.png do vanilla recolorido para couro azul-petróleo.

Uso: python tools/derive_story_art.py <jar do Minecraft (client ou merged)>
"""
import io
import json
import sys
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/spacehorizons"


def lum(r, g, b):
    return 0.299 * r + 0.587 * g + 0.114 * b


def tint(img, dark, light):
    out = Image.new("RGBA", img.size)
    s, d = img.load(), out.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b, a = s[x, y]
            if a == 0:
                d[x, y] = (0, 0, 0, 0)
                continue
            t = max(0.0, min(1.0, lum(r, g, b) / 255.0))
            d[x, y] = tuple(round(dark[i] + (light[i] - dark[i]) * t) for i in range(3)) + (a,)
    return out


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    with zipfile.ZipFile(sys.argv[1]) as jar:
        book = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/item/written_book.png"))).convert("RGBA")
    screen = Image.open(ROOT / "textures/block/ship_bridge.png").convert("RGBA")
    tint(screen, (6, 24, 14), (120, 255, 168)).save(ROOT / "textures/block/data_terminal.png")
    tint(book, (10, 30, 44), (96, 170, 190)).save(ROOT / "textures/item/journal_entry.png")

    model = json.loads((ROOT / "models/block/ship_bridge.json").read_text(encoding="utf-8"))
    model["textures"]["front"] = "spacehorizons:block/data_terminal"
    (ROOT / "models/block/data_terminal.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")

    rots = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for facing, y in rots.items():
        for record in ("aurora", "selene", "vigia"):
            v = {"model": "spacehorizons:block/data_terminal"}
            if y:
                v["y"] = y
            variants[f"facing={facing},record={record}"] = v
    (ROOT / "blockstates/data_terminal.json").write_text(json.dumps({"variants": variants}, indent=2) + "\n", encoding="utf-8")
    (ROOT / "items/data_terminal.json").write_text(
        json.dumps({"model": {"type": "minecraft:model", "model": "spacehorizons:block/data_terminal"}}, indent=2) + "\n", encoding="utf-8")
    (ROOT / "items/journal_entry.json").write_text(
        json.dumps({"model": {"type": "minecraft:model", "model": "spacehorizons:item/journal_entry"}}, indent=2) + "\n", encoding="utf-8")
    (ROOT / "models/item/journal_entry.json").write_text(
        json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "spacehorizons:item/journal_entry"}}, indent=2) + "\n", encoding="utf-8")
    print("gravado em", ROOT)


if __name__ == "__main__":
    main()
