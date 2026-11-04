import math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops

S = 2048          # supersampled canvas, saved at 1024
OUT = os.environ.get("OUT", "out")
os.makedirs(OUT, exist_ok=True)
FONT = "/usr/share/fonts/opentype/inter/InterDisplay-Black.otf"

def hx(h, a=255):
    h = h.lstrip("#"); return (int(h[0:2],16), int(h[2:4],16), int(h[4:6],16), a)

VIOLET, VDEEP, VLIGHT = hx("6B52ED"), hx("3F2BB8"), hx("9A87FF")
PINK, BLUE, TEAL = hx("F2559B"), hx("1C9EF5"), hx("00A99D")
GOLD, GOLD2 = hx("FFC93D"), hx("FF9F1C")
WHITE, INK = hx("FFFFFF"), hx("2A2C38")

def grad(c1, c2, angle=135, size=S):
    a = math.radians(angle)
    y, x = np.mgrid[0:size, 0:size] / (size - 1)
    t = (x - .5) * math.cos(a) + (y - .5) * math.sin(a)
    t = (t - t.min()) / (t.max() - t.min())
    c1, c2 = np.array(c1, float), np.array(c2, float)
    arr = c1 * (1 - t[..., None]) + c2 * t[..., None]
    return Image.fromarray(arr.astype(np.uint8), "RGBA")

def radial(c_in, c_out, cx=.5, cy=.4, r=.8):
    y, x = np.mgrid[0:S, 0:S] / (S - 1)
    t = np.clip(np.hypot(x - cx, y - cy) / r, 0, 1)[..., None]
    arr = np.array(c_in, float) * (1 - t) + np.array(c_out, float) * t
    return Image.fromarray(arr.astype(np.uint8), "RGBA")

def newmask(): return Image.new("L", (S, S), 0)

def paint(img, m, fill, opacity=1.0):
    src = fill if isinstance(fill, Image.Image) else Image.new("RGBA", (S, S), fill)
    m2 = m if opacity == 1 else m.point(lambda v: int(v * opacity))
    a = ImageChops.multiply(src.getchannel("A"), m2)
    layer = src.copy(); layer.putalpha(a)
    return Image.alpha_composite(img, layer)

def shadow(img, m, dy=40, blur=50, alpha=.35, color=(20, 8, 70)):
    sm = Image.new("L", (S, S), 0); sm.paste(m, (0, dy))
    sm = sm.filter(ImageFilter.GaussianBlur(blur))
    return paint(img, sm, color + (255,), alpha)

def edge(m, w=15):
    return ImageChops.subtract(m, m.filter(ImageFilter.MinFilter(w)))

def gloss(img, m, strength=.55):
    """Liquid-glass style: soft top highlight clipped to the shape plus a bright rim."""
    bbox = m.getbbox()
    if not bbox: return img
    x0, y0, x1, y1 = bbox; w, h = x1 - x0, y1 - y0
    hl = newmask()
    ImageDraw.Draw(hl).ellipse((x0 + w*.08, y0 - h*.35, x1 - w*.08, y0 + h*.45), fill=255)
    hl = ImageChops.multiply(hl.filter(ImageFilter.GaussianBlur(w*.06)), m)
    img = paint(img, hl, WHITE, strength * .55)
    img = paint(img, edge(m, 13).filter(ImageFilter.GaussianBlur(2)), WHITE, strength)
    return img

def ellipse_mask(box):
    m = newmask(); ImageDraw.Draw(m).ellipse(box, fill=255); return m

def rrect_mask(box, r):
    m = newmask(); ImageDraw.Draw(m).rounded_rectangle(box, r, fill=255); return m

def poly_mask(pts):
    m = newmask(); ImageDraw.Draw(m).polygon(pts, fill=255); return m

def star_pts(cx, cy, R, r=None, n=5, rot=-90):
    r = r or R * .48; pts = []
    for i in range(n * 2):
        rad = R if i % 2 == 0 else r
        a = math.radians(rot + i * 180 / n)
        pts.append((cx + rad * math.cos(a), cy + rad * math.sin(a)))
    return pts

def round_star(cx, cy, R, r=None, soft=None, n=5):
    m = poly_mask(star_pts(cx, cy, R, r, n))
    soft = soft or R * .10
    # round the points: blur then threshold
    return m.filter(ImageFilter.GaussianBlur(soft)).point(lambda v: 255 if v > 118 else 0).filter(ImageFilter.GaussianBlur(1.5))

def text_mask(t, cx, cy, size, font=FONT):
    m = newmask(); d = ImageDraw.Draw(m); f = ImageFont.truetype(font, size)
    l, tp, r, b = d.textbbox((0, 0), t, font=f)
    d.text((cx - (r - l) / 2 - l, cy - (b - tp) / 2 - tp), t, font=f, fill=255)
    return m

def pin_mask(cx, cy, s):
    """Map pin with a hole; (cx, cy) is the centre of the round head, s its radius."""
    m = newmask(); d = ImageDraw.Draw(m)
    d.ellipse((cx - s, cy - s, cx + s, cy + s), fill=255)
    d.polygon([(cx - s*.86, cy + s*.5), (cx + s*.86, cy + s*.5), (cx, cy + s*1.9)], fill=255)
    m = m.filter(ImageFilter.GaussianBlur(s*.06)).point(lambda v: 255 if v > 128 else 0)
    d = ImageDraw.Draw(m); d.ellipse((cx - s*.4, cy - s*.4, cx + s*.4, cy + s*.4), fill=0)
    return m

def times_mask(cx, cy, s, w):
    m = newmask(); d = ImageDraw.Draw(m)
    for a in (45, 135):
        dx, dy = math.cos(math.radians(a)) * s, math.sin(math.radians(a)) * s
        d.line((cx - dx, cy - dy, cx + dx, cy + dy), fill=255, width=int(w))
        for sx, sy in ((cx - dx, cy - dy), (cx + dx, cy + dy)):
            d.ellipse((sx - w/2, sy - w/2, sx + w/2, sy + w/2), fill=255)
    return m

def rotate(m, deg, center):
    return m.rotate(deg, resample=Image.BICUBIC, center=center)

def save(img, name):
    img.convert("RGB").resize((1024, 1024), Image.LANCZOS).save(f"{OUT}/{name}.png")
    print("saved", name)

C = S / 2

