import numpy as np

BACKGROUND = np.array([19.0, 255.0, 8.0], dtype=np.float32)
SOLID_BELOW = 14.0
CLEAR_ABOVE = 232.0
MIN_ALPHA = 0.06


def key_frame(rgb):
    px = rgb.astype(np.float32)
    r, g, b = px[..., 0], px[..., 1], px[..., 2]
    excess = g - np.maximum(r, b)
    alpha = 1.0 - (excess - SOLID_BELOW) / (CLEAR_ABOVE - SOLID_BELOW)
    alpha = np.clip(alpha, 0.0, 1.0)
    alpha[alpha < MIN_ALPHA] = 0.0
    safe = np.maximum(alpha, 1e-3)[..., None]
    fg = (px - (1.0 - alpha)[..., None] * BACKGROUND) / safe
    fg = np.clip(fg, 0.0, 255.0)
    limit = np.maximum(fg[..., 0], fg[..., 2])
    fg[..., 1] = np.minimum(fg[..., 1], limit)
    fg[alpha == 0.0] = 0.0
    return fg, alpha
