import os
import sys

import numpy as np
from PIL import Image, ImageDraw

from keying import key_frame
from read_frames import frames

FRAME_INDEX = 16
HEAD_BOX = (380, 330, 1000, 950)
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def head_image(source):
    for index, frame in enumerate(frames(source)):
        if index == FRAME_INDEX:
            fg, alpha = key_frame(frame)
            rgba = np.dstack([fg, alpha * 255]).astype(np.uint8)
            return Image.fromarray(rgba, "RGBA").crop(HEAD_BOX)


def background(size):
    y = np.linspace(0, 1, size)[:, None]
    x = np.linspace(0, 1, size)[None, :]
    mix = np.clip(0.65 * y + 0.35 * x, 0, 1)[..., None]
    top = np.array([255, 190, 70], dtype=np.float32)
    bottom = np.array([226, 80, 110], dtype=np.float32)
    return Image.fromarray((top * (1 - mix) + bottom * mix).astype(np.uint8), "RGB").convert("RGBA")


def compose(head, size, circle):
    canvas = background(size)
    inner = int(size * 0.86)
    scaled = head.resize((inner, inner), Image.LANCZOS)
    canvas.alpha_composite(scaled, ((size - inner) // 2, size - inner))
    if circle:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        canvas.putalpha(mask)
    return canvas


if __name__ == "__main__":
    source, res_dir = sys.argv[1], sys.argv[2]
    head = head_image(source)
    for density, size in SIZES.items():
        folder = os.path.join(res_dir, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        big = compose(head, size * 4, False).resize((size, size), Image.LANCZOS)
        big.save(os.path.join(folder, "ic_launcher.png"))
        round_big = compose(head, size * 4, True).resize((size, size), Image.LANCZOS)
        round_big.save(os.path.join(folder, "ic_launcher_round.png"))
    compose(head, 512, False).save(os.path.join(res_dir, "..", "icon_preview.png"))
