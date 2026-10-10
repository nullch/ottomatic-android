#!/usr/bin/env python3
"""Generate Cyrillic glyphs for Otto Matic's bitmap font, matching the original glow style.

Reads the ORIGINAL Data/System/{font.sfl,font1.tga,font2.tga}; writes new files with an extra
1024x1024 strip below (atlas becomes 1024x2048; set ATLAS_HEIGHT to 2048 in TextMesh.c).
Original region is copied byte-for-byte (same indices, same palettes, same TGA type 9 / top-left origin).
Font used for the glyph shapes: Liberation Sans Bold Italic (SIL OFL 1.1).
"""
import sys, struct, math
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage

SRC, DST, FONT = sys.argv[1], sys.argv[2], sys.argv[3]
FONT_PX   = 45        # em size in atlas pixels (cap height ~ 25px, original is 26)
HSCALE    = 1.00      # original font is wide; stretch a bit horizontally
SS        = 4         # supersampling
PAD_L = PAD_R = PAD_B = 20   # glow radius in the original
PAD_T     = 21               # glow above the ink in the original (cap top is at row 21, baseline row 52)
BASELINE  = 52               # baseline distance from line top (== line height)
ATLAS_W, ATLAS_H = 1024, 2048

# Glow profile measured on original stems (value vs distance outside the ink edge, px)
D_PTS = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20.5]
V_PTS = [48, 45, 43, 38, 31, 27, 22, 17, 12, 10, 7, 6, 4, 3, 2, 1, 1, 1, 1, 0.6, 0]
# Top edge of glyphs is dimmer in the original: ink value by rows from the top edge
K_PTS = [0, 1, 2, 3, 4, 5, 6, 7, 8]
F_PTS = [90, 168, 201, 222, 234, 244, 255, 255, 255]   # first row in k==0..1 range

CODEPOINTS = (list(range(0x410, 0x430)) + [0x401] + list(range(0x430, 0x450)) + [0x451] + [0x2116])

def read_tga_raw(path):
    d = open(path, "rb").read()
    idlen = d[0]; cmlen = struct.unpack("<H", d[5:7])[0]; cmbits = d[7]
    assert d[2] == 9 and d[16] == 8 and cmbits == 24, "unexpected original TGA format"
    off = 18 + idlen
    pal = d[off:off + cmlen * 3]
    return bytearray(d[:18]), pal, cmlen

def palette_rgb(pal, cmlen):
    arr = np.frombuffer(pal, np.uint8).reshape(cmlen, 3)   # BGR
    return arr[:, ::-1].copy()

def rle_encode_row(row):
    out = bytearray(); n = len(row); i = 0
    while i < n:
        run = 1
        while i + run < n and run < 128 and row[i + run] == row[i]:
            run += 1
        if run >= 2:
            out.append(0x80 | (run - 1)); out.append(row[i]); i += run
        else:
            j = i + 1
            while j < n and j - i < 128 and not (j + 1 < n and row[j] == row[j + 1]):
                j += 1
            cnt = j - i
            out.append(cnt - 1); out.extend(bytes(row[i:j])); i = j
    return out

def write_tga(path, header, pal, idx):
    h, w = idx.shape
    header = bytearray(header)
    header[12:14] = struct.pack("<H", w); header[14:16] = struct.pack("<H", h)
    body = bytearray()
    for y in range(h):
        body += rle_encode_row(idx[y].tolist())
    open(path, "wb").write(bytes(header) + bytes(pal) + bytes(body))

def render_glyph(font, ch):
    """Return (index_image uint8 HxW, xoff, yoff, xadv)"""
    big = SS * 3
    cw, ch_h, ox, oy = 700, 700, 200, 450
    img = Image.new("L", (cw, ch_h), 0)
    ImageDraw.Draw(img).text((ox, oy), ch, font=font, fill=255, anchor="ls")
    img = img.resize((int(cw * HSCALE), ch_h), Image.LANCZOS)
    m = np.array(img) >= 128
    ys, xs = np.nonzero(m)
    oxs = ox * HSCALE
    l, r = xs.min() - oxs, xs.max() + 1 - oxs
    t, b = ys.min() - oy, ys.max() + 1 - oy
    adv = font.getlength(ch) * HSCALE / SS

    lr = int(round(l / SS)); tr = int(round(t / SS))
    xoff = lr - PAD_L
    yoff = BASELINE - PAD_T + tr          # rect top relative to line top
    pen_x = (PAD_L - lr) * SS             # canvas x of pen origin (4x px)
    base_y = (PAD_T - tr) * SS            # canvas y of baseline (4x px)
    right_c = pen_x + r
    W4 = SS * int(math.ceil((right_c + PAD_R * SS) / SS))
    H4 = SS * int(math.ceil((base_y + b + PAD_B * SS) / SS))

    canvas = np.zeros((H4, W4), bool)
    sx0 = int(round(xs.min())); sy0 = int(ys.min())
    ink = m[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    # destination position of ink's top-left in canvas
    dx = int(round(pen_x + (xs.min() - oxs)))
    dy = int(round(base_y + (ys.min() - oy)))
    hh, ww = ink.shape
    canvas[dy:dy + hh, dx:dx + ww] |= ink[:H4 - dy, :W4 - dx]

    # glow by distance from the ink edge
    d = ndimage.distance_transform_edt(~canvas) / SS
    val = np.interp(d, D_PTS, V_PTS)
    # rows from the top edge of each ink run -> dimmer top edge like the original
    k = np.zeros(canvas.shape, np.float32)
    for y in range(1, canvas.shape[0]):
        k[y] = (k[y - 1] + 1) * canvas[y]
    k[0] = canvas[0]
    val = np.where(canvas, 255.0, val)
    val = val.reshape(H4 // SS, SS, W4 // SS, SS).mean(axis=(1, 3))
    return np.clip(np.rint(val), 0, 255).astype(np.uint8), xoff, yoff, int(round(adv))

def main():
    h1, pal1, n1 = read_tga_raw(f"{SRC}/font1.tga")
    h2, pal2, n2 = read_tga_raw(f"{SRC}/font2.tga")
    a1 = np.array(Image.open(f"{SRC}/font1.tga")); a2 = np.array(Image.open(f"{SRC}/font2.tga"))
    assert a1.shape == (1024, 1024) == a2.shape
    # font1 index -> font2 index mapping (deterministic in the original)
    m12 = {}
    for x, y in zip(a1.ravel(), a2.ravel()):
        m12.setdefault(int(x), int(y))
    known = sorted(m12)
    lut = np.zeros(256, np.uint8)
    for i in range(256):
        nearest = min(known, key=lambda k: abs(k - i))
        lut[i] = m12[nearest]

    font = ImageFont.truetype(FONT, FONT_PX * SS)
    glyphs = []
    for cp in CODEPOINTS:
        g, xoff, yoff, adv = render_glyph(font, chr(cp))
        glyphs.append((cp, g, xoff, yoff, adv))

    # shelf packing in the new strip (y >= 1024+8)
    new1 = np.zeros((ATLAS_H, ATLAS_W), np.uint8)
    new1[:1024] = a1
    x = 2; y = 1032; row_h = 0; placed = []
    for cp, g, xoff, yoff, adv in glyphs:
        gh, gw = g.shape
        if x + gw + 2 > ATLAS_W:
            x = 2; y += row_h + 2; row_h = 0
        assert y + gh <= ATLAS_H, "atlas too small"
        new1[y:y + gh, x:x + gw] = g
        placed.append((cp, x, y, gw, gh, xoff, yoff, adv))
        x += gw + 2; row_h = max(row_h, gh)
    used_h = y + row_h
    new2 = np.zeros_like(new1); new2[:1024] = a2; new2[1024:] = lut[new1[1024:]]

    write_tga(f"{DST}/font1.tga", h1, pal1, new1)
    write_tga(f"{DST}/font2.tga", h2, pal2, new2)

    sfl = open(f"{SRC}/font.sfl").read().split("\n")
    # header: name, "size lineheight", image, count
    count = int(sfl[3]); assert count == 175
    body = sfl[4:4 + count]
    tail = sfl[4 + count:]          # original has a trailing "0" line and an empty line: keep as is
    assert all(len(l.split()) == 8 for l in body)
    sfl[3] = str(count + len(placed))
    new_lines = [f"{cp} {x} {y} {w} {h} {xo} {yo} {adv} " for cp, x, y, w, h, xo, yo, adv in placed]
    out = sfl[:4] + body + new_lines + tail
    open(f"{DST}/font.sfl", "w").write("\n".join(out))
    print("glyphs added:", len(placed), "strip used up to y =", used_h, "of", ATLAS_H)

main()
