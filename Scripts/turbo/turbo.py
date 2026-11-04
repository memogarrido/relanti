"""Turbo and Relanti character explorations, rendered like Volantón's Rumbo
(puffy 3D shading with numpy + Pillow).

Usage, from this folder:  OUT=out python3 turbo.py
Writes one PNG per option and expression into $OUT.
"""
from puffy import *

FONT_BLACK = "/usr/share/fonts/opentype/inter/InterDisplay-Black.otf"
CHAR = hx("2B2F36"); NAVY = hx("0F2240"); ROADBLUE = hx("1A56B0"); SKYBLUE = hx("4F8DF0")
LAMP = hx("FFF6D8"); CHROME = hx("D8DCE2"); TYRE = hx("2A2C31"); GLASS = hx("BFD8F2")
SHADOW = (30, 30, 50)
BG = (hx("F4F6FA"), hx("DCE3EE"))


def canvas():
    return grad(BG[0], BG[1], 135)


def P(x, y, s=1.0, cx=0, cy=0):
    return (C + cx + x * s, C + cy + y * s)


def mk(s=1.0, cx=0, cy=0, rot=0):
    """Shape helpers in a ±1000 coordinate space, optionally rotated about the ground point."""
    def tr(m):
        return m if rot == 0 else rotate(m, rot, P(0, 700, s, cx, cy))
    def E(x0, y0, x1, y1):
        a, b = P(x0, y0, s, cx, cy), P(x1, y1, s, cx, cy); return tr(ellipse_mask((a[0], a[1], b[0], b[1])))
    def R(x0, y0, x1, y1, r):
        a, b = P(x0, y0, s, cx, cy), P(x1, y1, s, cx, cy); return tr(rrect_mask((a[0], a[1], b[0], b[1]), int(r * s)))
    def Poly(pts):
        return tr(poly_mask([P(x, y, s, cx, cy) for x, y in pts]))
    def Line(pts, w):
        m = newmask(); ImageDraw.Draw(m).line([P(x, y, s, cx, cy) for x, y in pts], fill=255, width=int(w * s), joint="curve")
        for x, y in (pts[0], pts[-1]):
            a = P(x, y, s, cx, cy); r = w * s / 2
            ImageDraw.Draw(m).ellipse((a[0] - r, a[1] - r, a[0] + r, a[1] + r), fill=255)
        return tr(m)
    def T(t, x, y, size):
        a = P(x, y, s, cx, cy); return tr(text_mask(t, a[0], a[1], int(size * s), FONT_BLACK))
    return E, R, Poly, Line, T


# ---------------------------------------------------------------- 1. Sat nav
def satnav(expr="idle", s=1.0, bg=None, mount=True):
    img = canvas() if bg is None else bg
    rot = {"curious": 7}.get(expr, 0)
    dy = -40 if expr == "happy" else 0
    E, R, Poly, Line, T = mk(s, 0, dy, rot)
    if mount: img = paint(img, E(-560, 760, 560, 900).filter(ImageFilter.GaussianBlur(60 * s)), SHADOW + (255,), .3)
    # suction mount
    cup = E(-300, 660, 300, 820) if mount else newmask()
    img = inflate(img, cup, hx("A9B1BE"), depth=.8, soft=60 * s, shine=.5)
    arm = R(-70, 380, 70, 720, 60)
    img = inflate(img, arm, hx("3A3F48"), depth=1, soft=40 * s, shine=.4)
    # device
    body = R(-720, -480, 720, 440, 190)
    img = contact(img, body, int(30 * s), int(50 * s), .3, SHADOW)
    img = inflate(img, body, CHAR, depth=1.4, soft=120 * s, shine=.35, gloss_pow=20)
    screen = R(-610, -380, 610, 340, 120)
    img = inflate(img, screen, NAVY, depth=.25, soft=40 * s, shine=.15, ambient=.8)
    # faint map on screen
    road = ImageChops.multiply(Line([(-610, 260), (-200, 160), (150, 220), (610, 60)], 70), screen)
    img = paint(img, road, hx("21406B"), 1)
    route = ImageChops.multiply(Line([(-610, 260), (-200, 160), (150, 220), (610, 60)], 22), screen)
    img = paint(img, route.filter(ImageFilter.GaussianBlur(6 * s)), SKYBLUE, .9)
    # eyes on the screen
    eyes = newmask()
    for ex in ((-260, 260) if expr != "blank" else ()):
        if expr == "happy":
            e = Line([(ex - 120, -10), (ex, -130), (ex + 120, -10)], 70)
        elif expr == "recalc":
            m = newmask(); a, b = P(ex - 120, -190, s, 0, dy), P(ex + 120, 50, s, 0, dy)
            ImageDraw.Draw(m).arc((a[0], a[1], b[0], b[1]), 30, 300, fill=255, width=int(60 * s))
            e = rotate(m, rot, P(0, 700, s, 0, dy)) if rot else m
        elif expr == "curious":
            e = R(ex - 75, -230 + (40 if ex < 0 else -20), ex + 75, 60 + (-20 if ex < 0 else 20), 75)
        elif expr == "explain":
            e = R(ex - 85, -130, ex + 85, 40, 80)
        else:
            e = R(ex - 85, -220, ex + 85, 60, 85)
        eyes = ImageChops.lighter(eyes, e)
    img = paint(img, eyes.filter(ImageFilter.GaussianBlur(40 * s)), hx("7FC4FF"), .7)
    img = paint(img, eyes.filter(ImageFilter.GaussianBlur(3)), hx("F4FAFF"))
    if expr == "explain":  # a small arrow on screen, like a turn instruction
        arr = Poly([(380, -330), (520, -250), (380, -170)])
        img = paint(img, ImageChops.lighter(arr, R(240, -280, 400, -220, 20)).filter(ImageFilter.GaussianBlur(2)), hx("F2B705"))
    # glass sheen over the screen
    sheen = ImageChops.multiply(Poly([(-610, -380), (-60, -380), (-460, 340), (-610, 340)]), screen)
    img = paint(img, sheen.filter(ImageFilter.GaussianBlur(20 * s)), WHITE, .07)
    # the L badge and a status light on top of the device
    tag = R(-150, -600, 150, -440, 40)
    img = contact(img, tag, int(14 * s), int(20 * s), .3, SHADOW)
    img = inflate(img, tag, ROADBLUE, depth=.8, soft=40 * s, shine=.5)
    img = paint(img, T("L", 0, -522, 130).filter(ImageFilter.GaussianBlur(1.5)), WHITE)
    led = E(560, -440, 620, -380)
    img = inflate(img, led, hx("3DDC84") if expr != "recalc" else hx("F2B705"), depth=1, soft=20 * s, shine=.9)
    return img


# ----------------------------------------------------- 2. Road-sign pictogram
def pictogram(expr="idle", s=1.0):
    img = canvas()
    E, R, Poly, Line, T = mk(s, 0, 0, 0)
    img = paint(img, E(-620, 760, 620, 880).filter(ImageFilter.GaussianBlur(50 * s)), SHADOW + (255,), .25)
    plate = R(-760, -760, 760, 760, 120)
    img = contact(img, plate, int(20 * s), int(40 * s), .3, SHADOW)
    img = paint(img, plate, WHITE)
    img = paint(img, R(-700, -700, 700, 700, 90), ROADBLUE)
    rot = {"curious": -6, "happy": 0}.get(expr, 0)
    dy = -50 if expr == "happy" else 0
    E2, R2, Poly2, Line2, T2 = mk(s, 0, dy, rot)
    car = newmask()
    for m in (R2(-430, -330, 430, 120, 140), R2(-560, -40, 560, 330, 110), R2(-500, 280, -310, 470, 50), R2(310, 280, 500, 470, 50)):
        car = ImageChops.lighter(car, m)
    img = paint(img, car.filter(ImageFilter.GaussianBlur(2)), WHITE)
    img = paint(img, R2(-330, -250, 330, 0, 70).filter(ImageFilter.GaussianBlur(2)), ROADBLUE)  # windscreen cut
    img = paint(img, R2(-150, -470, 150, -350, 30).filter(ImageFilter.GaussianBlur(2)), WHITE)     # roof sign
    img = paint(img, T2("L", 0, -410, 110).filter(ImageFilter.GaussianBlur(1)), ROADBLUE)
    for ex in (-330, 330):
        if expr == "happy":
            e = Line2([(ex - 100, 160), (ex, 80), (ex + 100, 160)], 50)
        elif expr == "curious":
            e = E2(ex - 105, 50 - (25 if ex > 0 else 0), ex + 105, 250 + (25 if ex > 0 else 0))
        elif expr == "explain":
            e = R2(ex - 105, 120, ex + 105, 220, 50)
        else:
            e = E2(ex - 105, 50, ex + 105, 250)
        img = paint(img, e.filter(ImageFilter.GaussianBlur(2)), ROADBLUE)
    img = paint(img, R2(-150, 230, 150, 280, 25).filter(ImageFilter.GaussianBlur(2)), ROADBLUE)  # grille slot
    return img


# --------------------------------------------------------------- 3. Toy car
def toycar(expr="idle", s=1.0, body_c=hx("EF6A32"), name="toy"):
    img = canvas()
    rot = {"curious": -7}.get(expr, 0)
    dy = -60 if expr == "happy" else 0
    E, R, Poly, Line, T = mk(s, 0, dy + 60, rot)
    img = paint(img, E(-660, 680, 660, 820).filter(ImageFilter.GaussianBlur(60 * s)), SHADOW + (255,), .32)
    for tx in (-470, 470):
        tyre = R(tx - 150, 330, tx + 150, 660, 90)
        img = inflate(img, tyre, TYRE, depth=1.2, soft=60 * s, shine=.25)
    cabin = smooth(E(-520, -640, 520, 60), 20 * s)
    img = contact(img, cabin, int(20 * s), int(40 * s), .25, SHADOW)
    img = inflate(img, cabin, body_c, depth=1.4, soft=150 * s, shine=.35, gloss_pow=22)
    glass = smooth(E(-410, -540, 410, -60), 10 * s)
    glass = ImageChops.multiply(glass, R(-500, -600, 500, -110, 10))
    img = inflate(img, glass, GLASS, depth=.6, soft=60 * s, shine=.8, gloss_pow=50, ambient=.65)
    img = paint(img, rotate(E(-300, -480, -200, -230), -25, P(-250, -350, s, 0, dy + 60)).filter(ImageFilter.GaussianBlur(10 * s)), WHITE, .55)
    body = smooth(ImageChops.lighter(E(-760, -220, 760, 420), R(-760, 0, 760, 520, 220)), 30 * s)
    img = contact(img, body, int(20 * s), int(40 * s), .3, SHADOW)
    img = inflate(img, body, body_c, depth=1.6, soft=170 * s, shine=.4, gloss_pow=22)
    # roof sign
    sign = R(-210, -800, 210, -620, 50)
    img = contact(img, sign, int(14 * s), int(24 * s), .3, SHADOW)
    img = inflate(img, sign, ROADBLUE, depth=.9, soft=50 * s, shine=.55)
    img = paint(img, T("L", 0, -712, 140).filter(ImageFilter.GaussianBlur(1.5)), WHITE)
    # headlight eyes
    for ex in (-380, 380):
        ey = 110
        socket = E(ex - 250, ey - 250, ex + 250, ey + 250)
        img = ao(img, socket, 30 * s, .3, 16, body)
        img = inflate(img, socket, CHROME, depth=1.1, soft=50 * s, shine=.8, gloss_pow=50)
        lamp = E(ex - 200, ey - 200, ex + 200, ey + 200)
        img = inflate(img, lamp, LAMP, depth=.8, soft=80 * s, shine=.35, ambient=.8)
        if expr == "happy":
            arc = Line([(ex - 110, ey + 40), (ex, ey - 70), (ex + 110, ey + 40)], 55)
            img = inflate(img, arc, INK, depth=1, soft=20 * s, shine=.6)
        else:
            lx = {"curious": 50, "explain": -40}.get(expr, 15)
            pupil = E(ex + lx - 95, ey - 80, ex + lx + 95, ey + 120)
            img = ao(img, pupil, 14 * s, .2, 8, lamp)
            img = inflate(img, pupil, INK, depth=1, soft=40 * s, shine=.9, gloss_pow=60, ambient=.7)
            img = paint(img, E(ex + lx - 55, ey - 45, ex + lx - 5, ey + 5).filter(ImageFilter.GaussianBlur(2)), WHITE)
            lid = {"explain": .42, "curious": .0}.get(expr, .18)
            if lid:
                cut = ImageChops.multiply(lamp, R(ex - 220, ey - 220, ex + 220, ey - 200 + 400 * lid, 10))
                img = inflate(img, cut, body_c, depth=.7, soft=40 * s, shine=.3)
        # bonnet brow
        bt = {"curious": (-60 if ex < 0 else 40), "explain": 20, "happy": -40}.get(expr, 0)
        brow = Line([(ex - 170, -170 + (bt if ex < 0 else -bt) * .5), (ex, -205 + bt * .3), (ex + 170, -170 + (-bt if ex < 0 else bt) * .5)], 46)
        img = inflate(img, brow, tuple(int(c * .78) for c in body_c[:3]) + (255,), depth=.8, soft=20 * s, shine=.3)
    # chrome bumper and plate
    bumper = R(-680, 420, 680, 520, 50)
    img = contact(img, bumper, int(10 * s), int(16 * s), .3, SHADOW)
    img = inflate(img, bumper, CHROME, depth=1, soft=30 * s, shine=.85, gloss_pow=50)
    plate = R(-170, 400, 170, 500, 16)
    img = inflate(img, plate, WHITE, depth=.4, soft=14 * s, shine=.3, ambient=.85)
    img = paint(img, R(-160, 410, -110, 490, 10), ROADBLUE)
    return img


# ------------------------------------------ 4. Windscreen eyes (animated-film style)
def windscreen(expr="idle", s=1.0, body_c=hx("2F9E8F")):
    img = canvas()
    rot = {"curious": -7}.get(expr, 0)
    dy = -60 if expr == "happy" else 0
    E, R, Poly, Line, T = mk(s, 0, dy + 80, rot)
    img = paint(img, E(-700, 660, 700, 800).filter(ImageFilter.GaussianBlur(60 * s)), SHADOW + (255,), .32)
    for tx in (-500, 500):
        img = inflate(img, R(tx - 150, 300, tx + 150, 640, 90), TYRE, depth=1.2, soft=60 * s, shine=.25)
    cabin = smooth(ImageChops.lighter(E(-560, -660, 560, 100), R(-600, -300, 600, 100, 120)), 20 * s)
    img = contact(img, cabin, int(20 * s), int(40 * s), .25, SHADOW)
    img = inflate(img, cabin, body_c, depth=1.4, soft=150 * s, shine=.35, gloss_pow=22)
    glass = smooth(ImageChops.lighter(E(-470, -580, 470, -20), R(-500, -330, 500, -40, 80)), 10 * s)
    img = inflate(img, glass, hx("EEF5FC"), depth=.5, soft=60 * s, shine=.6, gloss_pow=50, ambient=.85)
    body = smooth(ImageChops.lighter(E(-780, -120, 780, 440), R(-780, 40, 780, 500, 220)), 30 * s)
    img = contact(img, body, int(20 * s), int(40 * s), .3, SHADOW)
    img = inflate(img, body, body_c, depth=1.6, soft=170 * s, shine=.4, gloss_pow=22)
    sign = R(-210, -820, 210, -640, 50)
    img = contact(img, sign, int(14 * s), int(24 * s), .3, SHADOW)
    img = inflate(img, sign, ROADBLUE, depth=.9, soft=50 * s, shine=.55)
    img = paint(img, T("L", 0, -732, 140).filter(ImageFilter.GaussianBlur(1.5)), WHITE)
    # eyes inside the windscreen
    for ex in (-215, 215):
        ey = -300
        white = E(ex - 200, ey - 210, ex + 200, ey + 200)
        white = ImageChops.multiply(white, glass)
        if expr == "happy":
            arc = Line([(ex - 120, ey + 40), (ex, ey - 80), (ex + 120, ey + 40)], 60)
            img = inflate(img, ImageChops.multiply(arc, glass), INK, depth=1, soft=20 * s, shine=.6)
            continue
        lx = {"curious": 60, "explain": -50}.get(expr, 20)
        iris = E(ex + lx - 130, ey - 125, ex + lx + 130, ey + 135)
        img = inflate(img, iris, hx("3E7BC8"), depth=.8, soft=40 * s, shine=.6, ambient=.7)
        pupil = E(ex + lx - 68, ey - 62, ex + lx + 68, ey + 74)
        img = inflate(img, pupil, INK, depth=1, soft=30 * s, shine=.9, gloss_pow=60, ambient=.7)
        img = paint(img, E(ex + lx - 45, ey - 55, ex + lx + 5, ey - 5).filter(ImageFilter.GaussianBlur(2)), WHITE)
        lid = {"explain": .42, "curious": .0}.get(expr, .2)
        if lid:
            cut = ImageChops.multiply(glass, R(ex - 230, ey - 240, ex + 230, ey - 220 + 420 * lid, 10))
            img = inflate(img, cut, body_c, depth=.6, soft=30 * s, shine=.3)
    # headlights stay headlights
    for hxp in (-560, 560):
        img = inflate(img, E(hxp - 110, 30, hxp + 110, 190), LAMP, depth=.8, soft=40 * s, shine=.7)
    # mouth in the grille
    if expr == "happy":
        mouth = smooth(ImageChops.subtract(E(-300, 120, 300, 380), R(-320, 100, 320, 220, 10)), 6 * s)
    elif expr == "curious":
        mouth = E(-90, 200, 90, 330)
    elif expr == "explain":
        mouth = smooth(ImageChops.subtract(E(-220, 170, 220, 340), R(-240, 150, 240, 230, 10)), 6 * s)
    else:
        mouth = smooth(ImageChops.subtract(E(-260, 150, 260, 330), R(-280, 130, 280, 230, 10)), 6 * s)
    img = inflate(img, mouth, hx("3A2A2A"), depth=.6, soft=20 * s, shine=.3)
    bumper = R(-700, 400, 700, 500, 50)
    img = inflate(img, bumper, CHROME, depth=1, soft=30 * s, shine=.85, gloss_pow=50)
    return img

# ------------------------------------------------- Relanti: your car, side view
def relanti(kind="city", body_c=hx("E8574F"), kit=(), bg=None):
    img = canvas() if bg is None else bg
    s = 1.0
    E, R, Poly, Line, T = mk(s, 0, 120, 0)
    if "underglow" in kit:
        img = paint(img, E(-760, 400, 760, 600).filter(ImageFilter.GaussianBlur(70)), hx("3FD8FF"), .9)
    img = paint(img, E(-820, 440, 820, 560).filter(ImageFilter.GaussianBlur(50)), SHADOW + (255,), .3)
    if kind == "city":
        shell = [(-760, 360), (-780, 120), (-700, 20), (-380, -40), (-160, -330), (240, -330), (460, -60), (720, 20), (790, 140), (780, 360)]
        win = [[(-120, -280), (60, -280), (60, -60), (-320, -60)], [(110, -280), (220, -280), (400, -60), (110, -60)]]
        wheels = (-470, 480)
    elif kind == "estate":
        shell = [(-840, 360), (-860, 120), (-780, 20), (-480, -40), (-260, -320), (700, -330), (820, -120), (850, 140), (840, 360)]
        win = [[(-230, -270), (40, -270), (40, -60), (-420, -60)], [(90, -270), (380, -270), (380, -60), (90, -60)], [(430, -270), (680, -270), (770, -60), (430, -60)]]
        wheels = (-540, 560)
    else:  # small van
        shell = [(-780, 360), (-800, 100), (-660, -10), (-470, -380), (720, -400), (790, -300), (800, 360)]
        win = [[(-420, -320), (-120, -320), (-120, -80), (-590, -80)], [(-60, -320), (300, -320), (300, -80), (-60, -80)]]
        wheels = (-500, 520)
    body = smooth(Poly(shell), 60)
    img = contact(img, body, 30, 40, .25, SHADOW)
    dull = "dull" in kit
    img = inflate(img, body, body_c, depth=1.4, soft=150, shine=.12 if dull else .4, gloss_pow=10 if dull else 22, ambient=.5 if dull else .42)
    if "primer" in kit:
        img = inflate(img, ImageChops.multiply(smooth(R(-80, -40, 300, 330, 40), 10), body), hx("9EA3A8"), depth=.4, soft=40, shine=.05)
    if "spoiler" in kit and kind == "city":
        img = contact(img, smooth(Poly([(300, -200), (600, -250), (610, -160), (360, -130)]), 10), 10, 16, .3, SHADOW)
        img = inflate(img, smooth(Poly([(300, -200), (600, -250), (610, -160), (360, -130)]), 10), hx("2B2F36"), depth=.8, soft=20, shine=.5)
    for w in win:
        g = smooth(Poly(w), 24)
        img = inflate(img, g, hx("3A4656") if "tint" in kit else GLASS, depth=.4, soft=40, shine=.8, gloss_pow=50, ambient=.65)
    if "Lplate" in kit:
        a, b = win[-1][0], win[-1][2]
        cx_, cy_ = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
        img = paint(img, R(cx_ - 55, cy_ - 55, cx_ + 55, cy_ + 55, 14), hx("1A56B0"))
        img = paint(img, T("L", cx_, cy_, 90), WHITE)
    img = paint(img, Line([(-700, 90), (720, 90)], 14), tuple(int(c * .8) for c in body_c[:3]) + (255,), .6)
    # lights
    img = inflate(img, smooth(Poly([(-790, 60), (-680, 40), (-690, 140), (-790, 150)]), 14), LAMP, depth=.6, soft=20, shine=.7)
    img = inflate(img, R(shell[-2][0] - 70, 60, shell[-2][0] + 10, 170, 20), hx("C8233A"), depth=.6, soft=20, shine=.7)
    if "roofbox" in kit:
        top = min(y for _, y in shell)
        img = inflate(img, R(-240, top - 120, 400, top + 20, 70), hx("2B2F36"), depth=1, soft=50, shine=.4)
    if "stripe" in kit:
        img = paint(img, Line([(-740, 220), (760, 220)], 40), WHITE, .9)
    if "fog" in kit:
        img = inflate(img, E(-790, 230, -700, 300), hx("F5C02E"), depth=.6, soft=16, shine=.7)
    for wx in wheels:
        tyre = E(wx - 190, 160, wx + 190, 540)
        img = contact(img, tyre, 10, 20, .3, SHADOW)
        img = inflate(img, tyre, TYRE, depth=1.2, soft=70, shine=.25)
        if "rims" in kit:
            img = inflate(img, E(wx - 130, 220, wx + 130, 480), hx("F2B705"), depth=1, soft=40, shine=.85, gloss_pow=50)
            spokes = newmask()
            for k in range(5):
                a_ = math.radians(90 + k * 72)
                ImageDraw.Draw(spokes).line([P(wx, 350, 1, 0, 120), P(wx + 120 * math.cos(a_), 350 + 120 * math.sin(a_), 1, 0, 120)], fill=255, width=34)
            img = paint(img, spokes.filter(ImageFilter.GaussianBlur(2)), hx("2B2F36"), .85)
            img = inflate(img, E(wx - 40, 310, wx + 40, 390), hx("F2B705"), depth=1, soft=20, shine=.8)
        elif "steel" in kit:
            img = inflate(img, E(wx - 95, 255, wx + 95, 445), hx("6B7077"), depth=.6, soft=40, shine=.2)
        else:
            img = inflate(img, E(wx - 95, 255, wx + 95, 445), CHROME, depth=1, soft=40, shine=.8, gloss_pow=50)
    return img


STAGES = [
    ("copiloto", hx("C9B79C"), ("dull", "primer", "steel")),
    ("aprendiz", None, ("Lplate",)),
    ("rodando", None, ("Lplate", "rims")),
    ("conductor", None, ("Lplate", "rims", "tint", "stripe")),
    ("rutalarga", None, ("Lplate", "rims", "tint", "stripe", "roofbox", "fog")),
    ("leyenda", None, ("rims", "tint", "stripe", "roofbox", "fog", "spoiler", "underglow")),
]


# ------------------------------------------------ Driver's view: Turbo on the dash
def dashboard(expr="idle", sky=("FFD29A", "F58A5B"), car_c=hx("E8574F")):
    """The road ahead at sunset, Relanti's dashboard, and Turbo mounted on it."""
    top, bot = hx(sky[0]), hx(sky[1])
    img = grad(hx("7FA8E0"), top, 90)
    E, R, Poly, Line, T = mk(1.0, 0, 0, 0)
    hz = -120
    sun = E(-180, hz - 260, 180, hz + 100)
    img = paint(img, sun.filter(ImageFilter.GaussianBlur(80)), hx("FFE7B0"), .9)
    img = paint(img, sun, hx("FFF1C9"))
    hills = smooth(Poly([(-1100, hz + 20), (-700, hz - 160), (-380, hz - 40), (-60, hz - 200), (320, hz - 60), (700, hz - 190), (1100, hz - 30), (1100, hz + 40), (-1100, hz + 40)]), 30)
    img = paint(img, hills, hx("C77D6A"))
    img = paint(img, Poly([(-1100, hz + 10), (1100, hz + 10), (1100, 1100), (-1100, 1100)]), hx("B7A274"))
    road = Poly([(-60, hz + 10), (60, hz + 10), (900, 1100), (-900, 1100)])
    img = paint(img, road, hx("3A3F48"))
    for edge_x in (-1, 1):
        img = paint(img, Poly([(edge_x * 52, hz + 10), (edge_x * 60, hz + 10), (edge_x * 900, 1100), (edge_x * 860, 1100)]), WHITE, .9)
    y = hz + 30; L = 18
    while y < 1100:
        w = 4 + (y - hz) * .035; L = 14 + (y - hz) * .25
        img = paint(img, Poly([(-w, y), (w, y), (w * 1.1, y + L), (-w * 1.1, y + L)]), WHITE, .95)
        y += L * 2.1
    # motorway gantry far ahead
    g = R(-260, hz - 230, 260, hz - 110, 16)
    img = paint(img, g, ROADBLUE); img = paint(img, R(-250, hz - 220, 250, hz - 120, 12), ROADBLUE)
    img = paint(img, T("EXAMEN  12 km", 0, hz - 170, 64), WHITE)
    for px_ in (-240, 240): img = paint(img, R(px_ - 8, hz - 110, px_ + 8, hz + 20, 4), hx("6B7280"))
    # windscreen frame and dashboard
    frame = ImageChops.subtract(R(-1100, -1100, 1100, 1100, 0), smooth(Poly([(-900, -760), (900, -760), (1040, 420), (-1040, 420)]), 80))
    img = inflate(img, frame, car_c, depth=1.2, soft=120, shine=.35)
    dash = smooth(ImageChops.lighter(E(-1300, 300, 1300, 900), R(-1100, 560, 1100, 1100, 0)), 30)
    img = contact(img, dash, -30, 60, .35, SHADOW)
    img = inflate(img, dash, hx("2E3138"), depth=1.2, soft=160, shine=.25, gloss_pow=18)
    img = inflate(img, E(-900, 760, -300, 1300), hx("1F2227"), depth=1, soft=80, shine=.3)
    img = inflate(img, ImageChops.subtract(E(-860, 800, -340, 1260), E(-760, 900, -440, 1160)), hx("3A3F48"), depth=1, soft=40, shine=.4)
    # Turbo, rendered on a transparent layer and scaled onto the dash
    layer = satnav(expr, 1.0, bg=Image.new("RGBA", (S, S), (0, 0, 0, 0)), mount=False)
    k = .42
    small = layer.resize((int(S * k), int(S * k)), Image.LANCZOS)
    img.alpha_composite(small, (int(C + 300 - S * k / 2), int(C + 330 - S * k / 2)))
    return img

# ------------------------------------------------ La Ruta: the home windscreen in different states
TOD = {  # sky top, sky at horizon, sun/moon colour, sun height, light multiplier
    "amanecer": ("8FA6DA", "FFC4A0", "FFE1C2", 60, .9),
    "dia": ("4E8FE6", "BFE0FF", "FFF8E0", -380, 1.0),
    "atardecer": ("7FA8E0", "FFB27A", "FFF1C9", -100, .85),
    "noche": ("0B1330", "26355E", "E6ECFF", -420, .38),
}


def shade(c, k):
    return tuple(int(min(255, v * k)) for v in c[:3]) + (255,)


def ruta(land="meseta", tod="atardecer", km=12, car_c=hx("E8574F"), dull=False, lplate=False, expr="idle"):
    """Home screen view: the windscreen. Landscape follows the etapa, light follows the clock,
    the gantry counts km to the exam, and the frame is your own car."""
    top, hor, sunc, sun_y, k = TOD[tod]
    img = grad(hx(top), hx(hor), 90)
    E, R, Poly, Line, T = mk(1.0, 0, 0, 0)
    hz = -120
    night = tod == "noche"
    if night:
        for (x, y) in [(-700, -600), (-420, -700), (-150, -560), (260, -680), (520, -590), (760, -720), (-860, -420), (640, -440), (60, -760)]:
            img = paint(img, E(x - 8, y - 8, x + 8, y + 8), WHITE, .85)
        img = paint(img, E(520, -560, 660, -420), hx("F2F4FF"))
        img = paint(img, E(560, -590, 700, -450), hx(top))
    else:
        sx = -420 if tod == "amanecer" else (0 if tod == "atardecer" else 420)
        sun = E(sx - 150, sun_y - 150, sx + 150, sun_y + 150)
        img = paint(img, sun.filter(ImageFilter.GaussianBlur(80)), hx("FFE7B0"), .7)
        img = paint(img, sun, hx(sunc))
    ground = hx({"ciudad": "8E9A8A", "meseta": "C9A86A", "costa": "C7B98A", "montana": "7FA06A"}[land])
    if land == "ciudad":
        x = -1100
        hs = [160, 260, 120, 340, 200, 420, 180, 300, 140, 380, 220, 280, 160]
        for i, h in enumerate(hs):
            w = 150 + (i % 3) * 30
            if abs(x + w / 2) > 140:
                img = paint(img, R(x, hz - h, x + w - 12, hz + 20, 6), shade(hx("6F7C93"), k))
                if night:
                    for wy in range(int(hz - h + 30), hz - 10, 60):
                        for wx in range(int(x + 20), int(x + w - 40), 50):
                            if (wx * 7 + wy * 3) % 5 < 2: img = paint(img, R(wx, wy, wx + 22, wy + 30, 3), hx("FFD27A"), .9)
            x += w
    elif land == "montana":
        peaks = smooth(Poly([(-1100, hz + 20), (-820, hz - 420), (-560, hz - 180), (-300, hz - 520), (-40, hz - 120), (240, hz - 460), (560, hz - 200), (860, hz - 560), (1100, hz - 260), (1100, hz + 40), (-1100, hz + 40)]), 30)
        img = paint(img, peaks, shade(hx("7C86A6"), k))
        for (px_, py_) in [(-820, -420), (-300, -520), (240, -460), (860, -560)]:
            img = paint(img, smooth(Poly([(px_, hz + py_), (px_ - 90, hz + py_ + 120), (px_ + 90, hz + py_ + 120)]), 20), shade(WHITE, k))
    elif land == "costa":
        img = paint(img, Poly([(80, hz - 6), (1100, hz - 6), (1100, hz + 400), (80, hz + 60)]), shade(hx("2F7FC1"), k))
        img = paint(img, smooth(Poly([(-1100, hz + 20), (-760, hz - 150), (-420, hz - 60), (-120, hz - 30), (-120, hz + 40), (-1100, hz + 40)]), 30), shade(hx("B9846A"), k))
    else:  # meseta
        hills = smooth(Poly([(-1100, hz + 20), (-700, hz - 90), (-380, hz - 20), (-60, hz - 110), (320, hz - 30), (700, hz - 100), (1100, hz - 20), (1100, hz + 40), (-1100, hz + 40)]), 30)
        img = paint(img, hills, shade(hx("C77D6A"), k))
        for mx in (-640, 520):  # windmills
            img = paint(img, R(mx - 6, hz - 260, mx + 6, hz, 3), shade(WHITE, k))
            for a in (90, 210, 330):
                r_ = math.radians(a)
                img = paint(img, Line([(mx, hz - 260), (mx + 110 * math.cos(r_), hz - 260 - 110 * math.sin(r_))], 8), shade(WHITE, k))
    gnd = Poly([(-1100, hz + 10), (1100, hz + 10), (1100, 1100), (-1100, 1100)])
    if land == "costa":
        gnd = Poly([(-1100, hz + 10), (80, hz + 10), (900, 1100), (-1100, 1100)])
        img = paint(img, Poly([(80, hz + 10), (1100, hz + 10), (1100, 1100), (900, 1100)]), shade(hx("2F7FC1"), k * .95))
    img = paint(img, gnd, shade(ground, k))
    road = Poly([(-60, hz + 10), (60, hz + 10), (900, 1100), (-900, 1100)])
    img = paint(img, road, shade(hx("3A3F48"), max(k, .6)))
    if night:  # headlight pool
        img = paint(img, Poly([(-40, hz + 120), (40, hz + 120), (700, 1100), (-700, 1100)]).filter(ImageFilter.GaussianBlur(90)), hx("FFF0C0"), .35)
    for edge_x in (-1, 1):
        img = paint(img, Poly([(edge_x * 52, hz + 10), (edge_x * 60, hz + 10), (edge_x * 900, 1100), (edge_x * 860, 1100)]), WHITE, .9)
    y = hz + 30
    while y < 1100:
        w = 4 + (y - hz) * .035; L = 14 + (y - hz) * .25
        img = paint(img, Poly([(-w, y), (w, y), (w * 1.1, y + L), (-w * 1.1, y + L)]), WHITE, .95)
        y += L * 2.1
    # gantry: closer (bigger) as km drops
    sc = 1.0 + (1 - min(km, 60) / 60) * .7
    gw, gh, gy = 260 * sc, 60 * sc, hz - 170 * sc
    img = paint(img, R(-gw, gy - gh, gw, gy + gh, 16), ROADBLUE)
    img = paint(img, T(f"EXAMEN  {km} km", 0, gy, int(64 * sc)), WHITE)
    for px_ in (-gw + 10, gw - 10): img = paint(img, R(px_ - 8 * sc, gy + gh, px_ + 8 * sc, hz + 20, 4), hx("6B7280"))
    # windscreen frame in the learner's own car colour
    frame = ImageChops.subtract(R(-1100, -1100, 1100, 1100, 0), smooth(Poly([(-900, -760), (900, -760), (1040, 420), (-1040, 420)]), 80))
    if dull:  # the chosen colour, faded and dusty until Pintura nueva
        car_c = tuple(int(a * .45 + b * .55) for a, b in zip(car_c[:3], hx("C9C2B8")[:3])) + (255,)
    img = inflate(img, frame, shade(car_c, .95 if night else 1), depth=1.2, soft=120, shine=.08 if dull else .35, gloss_pow=8 if dull else 22, ambient=.52 if dull else .42)
    if dull:  # dust and a primer patch on the pillar
        img = paint(img, smooth(Poly([(-1100, -900), (-880, -760), (-1000, 400), (-1100, 420)]), 20), hx("9EA3A8"), .9)
    if lplate:
        img = paint(img, R(700, -700, 840, -560, 16), hx("1A56B0"))
        img = paint(img, T("L", 770, -630, 110), WHITE)
    dash = smooth(ImageChops.lighter(E(-1300, 300, 1300, 900), R(-1100, 560, 1100, 1100, 0)), 30)
    img = contact(img, dash, -30, 60, .35, SHADOW)
    img = inflate(img, dash, hx("2E3138"), depth=1.2, soft=160, shine=.25, gloss_pow=18)
    img = inflate(img, E(-900, 760, -300, 1300), hx("1F2227"), depth=1, soft=80, shine=.3)
    img = inflate(img, ImageChops.subtract(E(-860, 800, -340, 1260), E(-760, 900, -440, 1160)), hx("3A3F48"), depth=1, soft=40, shine=.4)
    layer = satnav(expr, 1.0, bg=Image.new("RGBA", (S, S), (0, 0, 0, 0)), mount=False)
    kk = .42
    small = layer.resize((int(S * kk), int(S * kk)), Image.LANCZOS)
    img.alpha_composite(small, (int(C + 300 - S * kk / 2), int(C + 330 - S * kk / 2)))
    return img


RUTA_STATES = [
    ("ruta-start", dict(land="ciudad", tod="amanecer", km=58, car_c=hx("E8574F"), dull=True)),
    ("ruta-meseta", dict(land="meseta", tod="dia", km=40, car_c=hx("E8574F"), lplate=True)),
    ("ruta-costa", dict(land="costa", tod="atardecer", km=22, car_c=hx("E8574F"), lplate=True, expr="happy")),
    ("ruta-montana", dict(land="montana", tod="noche", km=6, car_c=hx("E8574F"), lplate=True, expr="explain")),
]


def save_small(img, name, px=640):
    img.convert("RGB").resize((px, px), Image.LANCZOS).save(f"{OUT}/{name}.jpg", quality=86)
    print("saved", name)


if __name__ == "__main__":
    which = sys.argv[1:] or ["satnav", "pictogram", "toy", "windscreen", "relanti"]
    exprs = ["idle", "curious", "happy", "explain"]
    if "satnav" in which:
        for e in exprs + ["recalc"]: save_small(satnav(e), f"satnav-{e}")
    if "pictogram" in which:
        for e in exprs: save_small(pictogram(e), f"pictogram-{e}")
    if "toy" in which:
        for e in exprs: save_small(toycar(e), f"toy-{e}")
    if "windscreen" in which:
        for e in exprs: save_small(windscreen(e), f"windscreen-{e}")
    if "stages" in which:
        for name, col, kit in STAGES:
            save_small(relanti("city", col or hx("E8574F"), kit), f"stage-{name}")
    if "ruta" in which:
        for name, kw in RUTA_STATES: save_small(ruta(**kw), name, 900)
    if "stagefaded" in which:
        save_small(relanti("city", hx("C98E86"), ("dull", "primer", "steel")), "stage-copiloto")
    if "blank" in which:
        save_small(satnav("blank"), "satnav-blank", 900)
    if "dash" in which:
        for e in ["idle", "happy", "recalc", "explain", "curious"]: save_small(dashboard(e), f"dash-{e}", 900)
    if "relanti" in which:
        save_small(relanti("city", hx("E8574F")), "relanti-city")
        save_small(relanti("estate", hx("4F8DF0"), ("roofbox",)), "relanti-estate")
        save_small(relanti("van", hx("4CBFA6"), ("stripe",)), "relanti-van")
