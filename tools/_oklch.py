"""sRGB <-> OKLab/OKLCH. Standard formulation (Bjorn Ottosson)."""
import math

def _srgb_to_lin(c):
    c = c / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

def _lin_to_srgb(c):
    v = 12.92 * c if c <= 0.0031308 else 1.055 * (c ** (1 / 2.4)) - 0.055
    return v

def hex_to_rgb(h):
    h = h.lstrip('#')
    if len(h) == 3: h = ''.join(ch * 2 for ch in h)
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

def rgb_to_hex(r, g, b):
    f = lambda v: max(0, min(255, int(round(v))))
    return '#%02x%02x%02x' % (f(r), f(g), f(b))

def rgb_to_oklab(r, g, b):
    lr, lg, lb = _srgb_to_lin(r), _srgb_to_lin(g), _srgb_to_lin(b)
    l = 0.4122214708*lr + 0.5363325363*lg + 0.0514459929*lb
    m = 0.2119034982*lr + 0.6806995451*lg + 0.1073969566*lb
    s = 0.0883024619*lr + 0.2817188376*lg + 0.6299787005*lb
    l_, m_, s_ = l ** (1/3) if l > 0 else -((-l) ** (1/3)), \
                 m ** (1/3) if m > 0 else -((-m) ** (1/3)), \
                 s ** (1/3) if s > 0 else -((-s) ** (1/3))
    return (0.2104542553*l_ + 0.7936177850*m_ - 0.0040720468*s_,
            1.9779984951*l_ - 2.4285922050*m_ + 0.4505937099*s_,
            0.0259040371*l_ + 0.7827717662*m_ - 0.8086757660*s_)

def oklab_to_rgb(L, a, bb):
    l_ = L + 0.3963377774*a + 0.2158037573*bb
    m_ = L - 0.1055613458*a - 0.0638541728*bb
    s_ = L - 0.0894841775*a - 1.2914855480*bb
    l, m, s = l_**3, m_**3, s_**3
    lr =  4.0767416621*l - 3.3077115913*m + 0.2309699292*s
    lg = -1.2684380046*l + 2.6097574011*m - 0.3413193965*s
    lb = -0.0041960863*l - 0.7034186147*m + 1.7076147010*s
    return tuple(_lin_to_srgb(v) * 255 for v in (lr, lg, lb))

def hex_to_oklch(h):
    L, a, b = rgb_to_oklab(*hex_to_rgb(h))
    C = math.hypot(a, b)
    H = math.degrees(math.atan2(b, a)) % 360
    return L, C, H

def oklch_to_hex(L, C, H, gamut_clip=True):
    """Convert, reducing chroma until the result is in sRGB gamut."""
    for i in range(101):
        c = C * (1 - i / 100.0)
        r, g, b = oklab_to_rgb(L, c * math.cos(math.radians(H)), c * math.sin(math.radians(H)))
        if not gamut_clip or (-0.5 <= r <= 255.5 and -0.5 <= g <= 255.5 and -0.5 <= b <= 255.5):
            return rgb_to_hex(r, g, b)
    return rgb_to_hex(*oklab_to_rgb(L, 0, 0))
