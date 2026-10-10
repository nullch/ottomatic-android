#!/usr/bin/env python3
"""Verify Otto Matic font files the way the game reads them, and render a preview.
usage: verify_font.py <dir with font.sfl font1.tga font2.tga> [original_dir] [preview.png]
"""
import sys, struct
import numpy as np
from PIL import Image, ImageDraw

D = sys.argv[1]; ORIG = sys.argv[2] if len(sys.argv) > 2 else None
OUT = sys.argv[3] if len(sys.argv) > 3 else "preview.png"
ok = True
def fail(msg):
    global ok; ok = False; print("FAIL:", msg)

# --- parse SFL exactly like ParseSFL() in TextMesh.c
L = open(f"{D}/font.sfl").read().split("\n")
assert L[0] == "Font"
size, lineh = L[1].split(); lineh = float(lineh)
n = int(L[3]); glyphs = {}
rows = L[4:4 + n]
if len(rows) != n: fail("fewer glyph lines than header count")
for l in rows:
    p = l.split()
    if len(p) != 8: fail(f"bad glyph line: {l!r}"); continue
    glyphs[int(p[0])] = tuple(map(float, p[1:]))
print("header count", n, "parsed", len(glyphs))

# --- TGA checks (same constraints as the original)
for name in ("font1", "font2"):
    d = open(f"{D}/{name}.tga", "rb").read(18)
    t, w, h, bpp, desc = d[2], struct.unpack("<H", d[12:14])[0], struct.unpack("<H", d[14:16])[0], d[16], d[17]
    print(name, "type", t, "size", (w, h), "bpp", bpp, "desc", desc, "cmaplen", struct.unpack("<H", d[5:7])[0], "cmbits", d[7])
    if (t, bpp, desc, d[7]) != (9, 8, 32, 24): fail(f"{name}: format differs from original (type 9, 8bpp, top-left, 24-bit palette)")
    if (w, h) != (1024, 2048): fail(f"{name}: size must match ATLAS_WIDTH/ATLAS_HEIGHT (1024x2048)")
A = np.array(Image.open(f"{D}/font1.tga"))
print("atlas array", A.shape)

# --- every glyph inside the atlas; no overlaps
rects = [(cp, int(g[0]), int(g[1]), int(g[2]), int(g[3])) for cp, g in glyphs.items() if cp != 0 and g[2] > 0]
H, W = A.shape
occ = np.zeros((H, W), np.int32)
for cp, x, y, w, h in rects:
    if x < 0 or y < 0 or x + w > W or y + h > H: fail(f"glyph {cp} rect outside atlas")
    else: occ[y:y + h, x:x + w] += 1
print("overlapping pixels:", int((occ > 1).sum()), "(original atlas has some by design if >0; compare below)")

if ORIG:
    o1 = np.array(Image.open(f"{ORIG}/font1.tga")); o2 = np.array(Image.open(f"{ORIG}/font2.tga"))
    n2 = np.array(Image.open(f"{D}/font2.tga"))
    if not np.array_equal(A[:1024], o1): fail("top 1024 rows of font1 differ from the original")
    if not np.array_equal(n2[:1024], o2): fail("top 1024 rows of font2 differ from the original")
    # original glyph lines unchanged
    ol = open(f"{ORIG}/font.sfl").read().split("\n")[4:179]
    if ol != rows[:175]: fail("original 175 glyph lines changed")
    print("original region identical:", ok)
    # glyphs from the new strip must not touch the old region
    for cp, x, y, w, h in rects:
        if cp in range(0x400, 0x500) or cp == 0x2116:
            if y < 1024: fail(f"new glyph {cp} intrudes the old region")

# --- coverage of Russian text
need = set(range(0x410, 0x450)) | {0x401, 0x451, 0x2116}
missing = sorted(need - set(glyphs))
if missing: fail(f"missing glyphs: {missing}")

# --- render preview (additive, like the game's blended glow on a dark background)
def palette(path):
    d = open(path, "rb").read(); cm = struct.unpack("<H", d[5:7])[0]
    pal = np.frombuffer(d[18:18 + cm * 3], np.uint8).reshape(cm, 3)[:, ::-1]
    return pal
def rgb_of(arr, pal): return pal[np.clip(arr, 0, len(pal) - 1)]
def render(text, arrays, pal, scale=1.0):
    S = 0.5 * scale * 2
    pen, y0 = 44, 0
    img = np.zeros((int(lineh + 60), 1500, 3), np.int32)
    for ch in text:
        g = glyphs.get(ord(ch))
        if g is None: continue
        x, y, w, h, xo, yo, adv = g
        if ch != " ":
            x, y, w, h = map(int, (x, y, w, h))
            tile = rgb_of(arrays[y:y + h, x:x + w], pal)
            px, py = int(pen + xo), int(yo + 10)
            if py >= 0 and px >= 0:
                img[py:py + h, px:px + w] += tile
        pen += adv
    return np.clip(img, 0, 255).astype(np.uint8)

p1 = palette(f"{D}/font1.tga"); p2 = palette(f"{D}/font2.tga")
n2 = np.array(Image.open(f"{D}/font2.tga"))
lines = ["Play Game  Settings  High Scores  Level 3",
         "Играть  Настройки  Рекорды  Уровень 3",
         "Загрузить игру  Выход  Продолжить  Ёж №5",
         "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ",
         "абвгдеёжзийклмнопрстуфхцчшщъыьэюя"]
imgs = []
for t in lines:
    imgs.append(render(t, A, p1))
rimg = [render(t, n2, p2) for t in lines[1:3]]
canvas = np.concatenate(imgs + rimg, axis=0)
bg = np.zeros_like(canvas); bg[:] = (14, 16, 28)
out = np.clip(canvas.astype(np.int32) + bg, 0, 255).astype(np.uint8)
Image.fromarray(out).save(OUT)
print("preview ->", OUT)
print("RESULT:", "OK" if ok else "PROBLEMS FOUND")
sys.exit(0 if ok else 1)
