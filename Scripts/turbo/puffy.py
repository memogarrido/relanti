"""Puffy 3D shading helpers, copied from Volantón (Scripts/rumbo/owl.py)."""
from iconkit import *

LIGHT = np.array([-0.45, -0.65, 0.62]); LIGHT = LIGHT / np.linalg.norm(LIGHT)
HALF = LIGHT + np.array([0, 0, 1.0]); HALF = HALF / np.linalg.norm(HALF)

_fx = np.fft.fftfreq(S)
def fblur(a, sigma):
    """Float Gaussian blur via FFT, avoids 8-bit terracing in the height map."""
    k = np.exp(-2 * (np.pi * sigma) ** 2 * (_fx[:, None] ** 2 + _fx[None, :] ** 2))
    return np.real(np.fft.ifft2(np.fft.fft2(a) * k))

def inflate(img, m, color, depth=1.0, soft=60, shine=.55, gloss_pow=40, ambient=.42, rim=.25):
    """Shade a flat mask as a puffy 3D shape: height from a blurred mask, normals from its gradient."""
    color = np.array(color[:3], float) / 255
    hgt = fblur(np.asarray(m, float) / 255, soft)
    hgt = np.sqrt(np.clip(hgt, 0, 1)) * soft * depth
    gy, gx = np.gradient(hgt)
    n = np.dstack([-gx, -gy, np.ones_like(hgt)])
    n /= np.linalg.norm(n, axis=2, keepdims=True)
    diff = np.clip(n @ LIGHT, 0, 1)
    spec = np.clip(n @ HALF, 0, 1) ** gloss_pow
    fres = (1 - n[..., 2]) ** 2
    out = color * (ambient + (1 - ambient) * diff[..., None]) * (1 - .35 * fres[..., None])
    out = out + spec[..., None] * shine + fres[..., None] * rim * .3
    rgb = (np.clip(out, 0, 1) * 255).astype(np.uint8)
    layer = Image.fromarray(np.dstack([rgb, np.asarray(m)]), "RGBA")
    return Image.alpha_composite(img, layer)

def contact(img, m, dy=60, blur=70, alpha=.35, color=(40, 20, 90)):
    return shadow(img, m, dy, blur, alpha, color)

def ao(img, m, blur=40, alpha=.35, dy=18, clip=None, color=(30, 10, 70)):
    """Soft occlusion cast by a part onto whatever is under it (clipped to `clip`)."""
    sm = Image.new("L", (S, S), 0); sm.paste(m, (0, dy)); sm = sm.filter(ImageFilter.GaussianBlur(blur))
    if clip is not None: sm = ImageChops.multiply(sm, clip)
    return paint(img, sm, color + (255,), alpha)

def smooth(m, r=30):
    return m.filter(ImageFilter.GaussianBlur(r)).point(lambda v: 255 if v > 128 else 0).filter(ImageFilter.GaussianBlur(1.5))
