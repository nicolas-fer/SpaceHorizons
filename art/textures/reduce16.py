"""Redução alternativa (usada em candidates.py): recorta a borda, reduz por média de área (BOX) e quantiza a <=N cores."""
import sys, argparse
from pathlib import Path
import numpy as np
from PIL import Image, ImageEnhance
sys.path.insert(0, str(Path.home()/".claude/skills/minecraft-entity/scripts"))
import pixelize as px
ap=argparse.ArgumentParser(); ap.add_argument("inp"); ap.add_argument("out"); ap.add_argument("--colors",type=int,default=7)
ap.add_argument("--size",type=int,default=16); ap.add_argument("--inset",type=float,default=0.0)
ap.add_argument("--contrast",type=float,default=1.15); ap.add_argument("--sat",type=float,default=1.1)
a=ap.parse_args()
img=np.asarray(Image.open(a.inp).convert("RGB")).astype(float)
bg=px.corner_color(img); work=px.crop_border(img,bg,48)
h,w,_=work.shape
if a.inset: 
    i=int(min(h,w)*a.inset); work=work[i:h-i,i:w-i]
im=Image.fromarray(np.clip(work,0,255).astype(np.uint8))
im=im.resize((a.size,a.size),Image.BOX)
im=ImageEnhance.Contrast(im).enhance(a.contrast); im=ImageEnhance.Color(im).enhance(a.sat)
q=im.quantize(colors=a.colors,method=Image.Quantize.MEDIANCUT,dither=Image.Dither.NONE).convert("RGBA")
q.save(a.out); q.resize((256,256),Image.NEAREST).save(a.out.replace(".png","_big.png"))
