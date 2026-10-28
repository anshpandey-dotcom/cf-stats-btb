import json
import os
import shutil
import subprocess
import sys

import numpy as np
from PIL import Image

from keying import key_frame
from read_frames import frames

FPS = 30
SCALE = 0.7
PAD = 12
FEET_Y = 1396
CAT_HEIGHT = 1040.0
WEBP_QUALITY = 88

CLIPS = {
    "clap": {"first": 0, "last": 79},
    "handshake": {"first": 152, "last": 204},
}


def load_keyed(source, first, last):
    keyed = {}
    for index, frame in enumerate(frames(source)):
        if index < first:
            continue
        if index > last:
            break
        fg, alpha = key_frame(frame)
        keyed[index] = (fg, alpha)
    return keyed


def union_box(keyed, width, height):
    x0, y0, x1, y1 = width, height, 0, 0
    feet = []
    for _, alpha in keyed.values():
        ys, xs = np.where(alpha > 0.15)
        x0, y0 = min(x0, xs.min()), min(y0, ys.min())
        x1, y1 = max(x1, xs.max()), max(y1, ys.max())
        band = alpha[FEET_Y - 70:FEET_Y]
        bx = np.where(band > 0.5)[1]
        if len(bx):
            feet.append(bx.mean())
    return x0, y0, x1, y1, float(np.mean(feet))


def make_sprite(fg, alpha, box):
    x0, y0, x1, y1 = box
    size = (int((x1 - x0) * SCALE), int((y1 - y0) * SCALE))
    premult = fg[y0:y1, x0:x1] * alpha[y0:y1, x0:x1, None]
    color = Image.fromarray(premult.astype(np.uint8)).resize(size, Image.LANCZOS)
    matte = Image.fromarray((alpha[y0:y1, x0:x1] * 255).astype(np.uint8)).resize(size, Image.LANCZOS)
    c = np.asarray(color).astype(np.float32)
    a = np.asarray(matte).astype(np.float32)
    straight = np.clip(c / np.maximum(a[..., None] / 255.0, 1e-3), 0, 255)
    straight[a < 2] = 0
    rgba = np.dstack([straight, a]).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def build(name, spec, source, assets_dir, audio_dir):
    keyed = load_keyed(source, spec["first"], spec["last"])
    sample = next(iter(keyed.values()))[1]
    height, width = sample.shape
    x0, y0, x1, y1, feet_x = union_box(keyed, width, height)
    box = (max(0, x0 - PAD), max(0, y0 - PAD), min(width, x1 + PAD), min(height, y1 + PAD))
    out = os.path.join(assets_dir, name)
    shutil.rmtree(out, ignore_errors=True)
    os.makedirs(out)
    for position, index in enumerate(range(spec["first"], spec["last"] + 1)):
        sprite = make_sprite(*keyed[index], box)
        sprite.save(os.path.join(out, f"f{position:03d}.webp"), quality=WEBP_QUALITY, alpha_quality=100, method=4)
    count = spec["last"] - spec["first"] + 1
    report = {
        "frames": count,
        "width": sprite.size[0],
        "height": sprite.size[1],
        "feetX": round((feet_x - box[0]) * SCALE, 1),
        "feetY": round((FEET_Y - box[1]) * SCALE, 1),
        "catHeight": round(CAT_HEIGHT * SCALE, 1),
        "start": spec["first"] / FPS,
        "duration": count / FPS,
    }
    return report


def audio(source, name, start, duration, total, audio_dir):
    subprocess.check_call([
        "ffmpeg", "-v", "error", "-y", "-ss", f"{start:.4f}", "-t", f"{duration:.4f}", "-i", source,
        "-vn", "-af", f"apad=whole_dur={total:.4f}", "-t", f"{total:.4f}",
        "-c:a", "aac", "-b:a", "96k", os.path.join(audio_dir, f"{name}.m4a"),
    ])


if __name__ == "__main__":
    source, assets_dir, audio_dir = sys.argv[1], sys.argv[2], sys.argv[3]
    os.makedirs(audio_dir, exist_ok=True)
    report = {name: build(name, spec, source, assets_dir, audio_dir) for name, spec in CLIPS.items()}
    audio(source, "clap_audio", 0.0, report["clap"]["duration"], report["clap"]["duration"], audio_dir)
    hs = report["handshake"]
    audio(source, "handshake_audio", hs["start"], hs["duration"], hs["duration"] * 2 - 2 / FPS, audio_dir)
    print(json.dumps(report, indent=2))
