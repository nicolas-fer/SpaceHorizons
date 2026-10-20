#!/usr/bin/env python3
"""
Gera o blockstate e os modelos de um bloco com TEXTURAS CONECTADAS, a partir de UMA textura com moldura.
Nao cria PNG: a textura continua sendo a mesma, so o UV muda (docs/TEXTURAS_CONECTADAS.md).

Como funciona
  Cada uma das 6 faces do cubo e UM quadrado so (sem emendas: fatiar a face em pedacos deixa linhas finas no vidro translucido, onde
  os pedacos se encostam). O blockstate (multipart) escolhe, por face, uma de 16 variantes conforme quais das 4 bordas da face encostam
  num vizinho conectado, lendo as 6 propriedades booleanas north/east/south/west/up/down do bloco (true = o vizinho conecta; ver
  ConnectedFaces.java). As 16 variantes sao PNGs DERIVADOS da textura original por processamento simples, sem desenho novo: moldura onde a
  borda esta fechada e o interior da propria textura repetido (periodo 16 - 2*moldura) onde esta aberta; um canto so abre se as duas bordas
  que o tocam abrem. Os PNGs ficam em textures/block/<name>_ct/ e sao regerados por este script; nao os edite a mao.

Uso
  python tools/gen_connected_block.py --name ship_window --texture spacehorizons:block/ship_window --frame 2 --render-type translucent

  --name         id do bloco (usa blockstates/<name>.json e models/block/<name>_ct/*.json)
  --texture      textura 16x16 (id de recurso); a moldura ocupa --frame pixels em cada lado
  --texture-end  textura das faces de cima e de baixo (padrao: a de --texture); gera variantes proprias (e00..e15)
  --frame        largura da moldura em pixels (1 a 7)
  --render-type  translucent | cutout | solid (padrao: solid)
  --namespace    namespace dos modelos (padrao: spacehorizons)
  --assets       pasta assets/<namespace> (padrao: src/main/resources/assets/spacehorizons)
  --particle     textura de particula (padrao: a mesma textura)

Requer Pillow (pip install pillow). O modelo de item continua sendo o cubo normal (models/block/<name>.json, escrito a mao).
"""
import argparse
import json
import os
import shutil

FACES = ["north", "south", "west", "east", "up", "down"]

# Em cada face, de que lado do mundo fica a esquerda/direita/cima/baixo da imagem da textura (conforme o UV padrao do vanilla).
SIDES = {
    "north": {"left": "east", "right": "west", "top": "up", "bottom": "down"},
    "south": {"left": "west", "right": "east", "top": "up", "bottom": "down"},
    "west": {"left": "north", "right": "south", "top": "up", "bottom": "down"},
    "east": {"left": "south", "right": "north", "top": "up", "bottom": "down"},
    "up": {"left": "west", "right": "east", "top": "north", "bottom": "south"},
    "down": {"left": "west", "right": "east", "top": "south", "bottom": "north"},
}


def box_for(face, u0, v0, u1, v1):
    """from/to (em 0..16) do retangulo [u0,v0,u1,v1] da imagem da face, inverso do UV padrao do vanilla."""
    if face == "north":
        return [16 - u1, 16 - v1, 0], [16 - u0, 16 - v0, 0]
    if face == "south":
        return [u0, 16 - v1, 16], [u1, 16 - v0, 16]
    if face == "west":
        return [0, 16 - v1, u0], [0, 16 - v0, u1]
    if face == "east":
        return [16, 16 - v1, 16 - u1], [16, 16 - v0, 16 - u0]
    if face == "up":
        return [u0, 16, v0], [u1, 16, v1]
    if face == "down":
        return [u0, 0, 16 - v1], [u1, 0, 16 - v0]
    raise ValueError(face)


def wrap(a, frame):
    """Mapeia uma coordenada de pixel da face para o interior da textura, com periodo (16 - 2*frame)."""
    w = 16 - 2 * frame
    return frame + ((a - frame) % w)


def wrapped_range(a0, a1, frame):
    """Intervalo [a0, a1) levado ao interior; continuo porque cada pedaco tem no maximo 'frame' pixels fora do miolo."""
    b0 = wrap(a0, frame)
    return b0, b0 + (a1 - a0)


def variant_image(base, frame, mask):
    """
    Monta a variante 16x16 da textura para o conjunto de bordas abertas 'mask' (bit0 esquerda, bit1 direita, bit2 cima, bit3 baixo).
    So copia pixels da propria textura: moldura onde a borda esta fechada, interior repetido (periodo 16 - 2*frame) onde esta aberta.
    Um canto so e aberto se as duas bordas que o tocam sao abertas.
    """
    from PIL import Image
    out = Image.new("RGBA", (16, 16))
    left, right, top, bottom = (bool(mask & 1), bool(mask & 2), bool(mask & 4), bool(mask & 8))
    for y in range(16):
        for x in range(16):
            in_l, in_r = x < frame, x >= 16 - frame
            in_t, in_b = y < frame, y >= 16 - frame
            open_h = left if in_l else right if in_r else None
            open_v = top if in_t else bottom if in_b else None
            if open_h is None and open_v is None:
                is_open = True
            elif open_h is None:
                is_open = open_v
            elif open_v is None:
                is_open = open_h
            else:
                is_open = open_h and open_v
            sx, sy = (wrap(x, frame), wrap(y, frame)) if is_open else (x, y)
            out.putpixel((x, y), base.getpixel((sx, sy)))
    return out


def face_model(args, face, mask):
    prefix = "e" if face in ("up", "down") and args.texture_end else "t"
    model = {
        "textures": {"all": f"{args.namespace}:block/{args.name}_ct/{prefix}{mask:02d}", "particle": args.particle or args.texture},
        "elements": [{
            "from": box_for(face, 0, 0, 16, 16)[0],
            "to": box_for(face, 0, 0, 16, 16)[1],
            "faces": {face: {"uv": [0, 0, 16, 16], "texture": "#all", "cullface": face}},
        }],
    }
    if args.render_type != "solid":
        model["render_type"] = "minecraft:" + args.render_type
    return model


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--name", required=True)
    ap.add_argument("--texture", required=True, help="id da textura, ex. spacehorizons:block/ship_window")
    ap.add_argument("--texture-end", default=None, help="textura das faces de cima e de baixo (padrao: a mesma --texture); ex. o topo de um tanque")
    ap.add_argument("--frame", type=int, default=2)
    ap.add_argument("--render-type", default="solid", choices=["solid", "cutout", "translucent"])
    ap.add_argument("--namespace", default="spacehorizons")
    ap.add_argument("--assets", default="src/main/resources/assets/spacehorizons")
    ap.add_argument("--particle", default=None)
    args = ap.parse_args()
    if not 1 <= args.frame <= 7:
        ap.error("--frame deve ficar entre 1 e 7")
    from PIL import Image

    tex_path = os.path.join(args.assets, "textures", "block", args.texture.split("block/")[1] + ".png")
    base = Image.open(tex_path).convert("RGBA")
    if base.size != (16, 16):
        ap.error("a textura precisa ser 16x16")

    model_dir = os.path.join(args.assets, "models", "block", args.name + "_ct")
    tex_dir = os.path.join(args.assets, "textures", "block", args.name + "_ct")
    for d in (model_dir, tex_dir):
        if os.path.isdir(d):
            shutil.rmtree(d)
        os.makedirs(d)
    for mask in range(16):
        variant_image(base, args.frame, mask).save(os.path.join(tex_dir, f"t{mask:02d}.png"))
    if args.texture_end:
        end_path = os.path.join(args.assets, "textures", "block", args.texture_end.split("block/")[1] + ".png")
        end_base = Image.open(end_path).convert("RGBA")
        if end_base.size != (16, 16):
            ap.error("a textura das pontas precisa ser 16x16")
        for mask in range(16):
            variant_image(end_base, args.frame, mask).save(os.path.join(tex_dir, f"e{mask:02d}.png"))

    parts = []
    for face in FACES:
        sides = SIDES[face]
        for mask in range(16):
            file_name = f"{face}_t{mask:02d}"
            with open(os.path.join(model_dir, file_name + ".json"), "w", newline="\n") as fh:
                json.dump(face_model(args, face, mask), fh, indent=2)
                fh.write("\n")
            when = {
                sides["left"]: "true" if mask & 1 else "false",
                sides["right"]: "true" if mask & 2 else "false",
                sides["top"]: "true" if mask & 4 else "false",
                sides["bottom"]: "true" if mask & 8 else "false",
            }
            parts.append({"when": when, "apply": {"model": f"{args.namespace}:block/{args.name}_ct/{file_name}"}})

    state_path = os.path.join(args.assets, "blockstates", args.name + ".json")
    os.makedirs(os.path.dirname(state_path), exist_ok=True)
    with open(state_path, "w", newline="\n") as fh:
        json.dump({"multipart": parts}, fh, indent=2)
        fh.write("\n")
    print(f"{state_path}: {len(parts)} partes; 16 texturas em {tex_dir}; modelos em {model_dir}")


if __name__ == "__main__":
    main()
