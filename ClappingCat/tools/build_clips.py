import json
import subprocess
import sys

import numpy as np
from PIL import Image

from keying import key_frame
from read_frames import frames

FPS = 30
SCALE = 0.75
PAD = 12
ALIGN = 16
FEET_Y = 1396
CAT_HEIGHT = 1040.0

CLIPS = {
    "clap": {"first": 0, "last": 79, "pingpong": False, "audio": True},
    "handshake": {"first": 152, "last": 204, "pingpong": True, "audio": True},
}


def round_up(value, step):
    return int(-(-value // step) * step)


def load_keyed(source, first, last):
    keyed = {}
    for index, frame in enumerate(frames(source)):
        if index < first:
            continue
        if index > last:
            break
        fg, alpha = key_frame(frame)
        keyed[index] = (fg * alpha[..., None], alpha)
    return keyed


def union_box(keyed, width, height):
    x0, y0, x1, y1 = width, height, 0, 0
    feet = []
    for premult, alpha in keyed.values():
        ys, xs = np.where(alpha > 0.15)
        x0, y0 = min(x0, xs.min()), min(y0, ys.min())
        x1, y1 = max(x1, xs.max()), max(y1, ys.max())
        band = alpha[FEET_Y - 70:FEET_Y]
        bx = np.where(band > 0.5)[1]
        if len(bx):
            feet.append(bx.mean())
    return x0, y0, x1, y1, float(np.mean(feet))


def encode(name, spec, source, out_dir):
    keyed = load_keyed(source, spec["first"], spec["last"])
    sample = next(iter(keyed.values()))[1]
    height, width = sample.shape
    x0, y0, x1, y1, feet_x = union_box(keyed, width, height)
    x0 = max(0, x0 - PAD)
    y0 = max(0, y0 - PAD)
    x1 = min(width, x1 + PAD)
    y1 = min(height, y1 + PAD)
    out_w = round_up((x1 - x0) * SCALE, ALIGN)
    out_h = round_up((y1 - y0) * SCALE, ALIGN)
    order = list(range(spec["first"], spec["last"] + 1))
    if spec["pingpong"]:
        order += list(range(spec["last"] - 1, spec["first"], -1))
    forward = spec["last"] - spec["first"] + 1
    command = [
        "ffmpeg", "-v", "error", "-y",
        "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{out_w * 2}x{out_h}", "-r", str(FPS), "-i", "-",
        "-ss", f"{spec['first'] / FPS:.4f}", "-t", f"{forward / FPS:.4f}", "-i", source,
        "-map", "0:v", "-map", "1:a",
        "-af", f"apad=whole_dur={len(order) / FPS:.4f}",
        "-c:v", "libx264", "-profile:v", "main", "-pix_fmt", "yuv420p", "-crf", "17", "-g", "15",
        "-c:a", "aac", "-b:a", "128k", "-t", f"{len(order) / FPS:.4f}", "-movflags", "+faststart",
        f"{out_dir}/{name}.mp4",
    ]
    proc = subprocess.Popen(command, stdin=subprocess.PIPE)
    cache = {}
    for index in order:
        if index not in cache:
            premult, alpha = keyed[index]
            crop_c = Image.fromarray(premult[y0:y1, x0:x1].astype(np.uint8))
            crop_a = Image.fromarray((alpha[y0:y1, x0:x1] * 255).astype(np.uint8))
            crop_c = crop_c.resize((int((x1 - x0) * SCALE), int((y1 - y0) * SCALE)), Image.LANCZOS)
            crop_a = crop_a.resize(crop_c.size, Image.LANCZOS)
            color = np.zeros((out_h, out_w, 3), dtype=np.uint8)
            matte = np.zeros((out_h, out_w), dtype=np.uint8)
            ox = (out_w - crop_c.size[0]) // 2
            color[:crop_c.size[1], ox:ox + crop_c.size[0]] = np.asarray(crop_c)
            matte[:crop_c.size[1], ox:ox + crop_c.size[0]] = np.asarray(crop_a)
            cache[index] = np.concatenate([color, np.repeat(matte[..., None], 3, axis=2)], axis=1)
        proc.stdin.write(cache[index].tobytes())
    proc.stdin.close()
    proc.wait()
    shift = (out_w - int((x1 - x0) * SCALE)) // 2
    return {
        "frames": len(order),
        "videoWidth": out_w * 2,
        "videoHeight": out_h,
        "halfWidth": out_w,
        "widthUnits": round(out_w / SCALE / CAT_HEIGHT, 4),
        "heightUnits": round(out_h / SCALE / CAT_HEIGHT, 4),
        "feetXUnits": round(((feet_x - x0) * SCALE + shift) / SCALE / CAT_HEIGHT, 4),
        "feetFromTopUnits": round((FEET_Y - y0) / CAT_HEIGHT, 4),
    }


if __name__ == "__main__":
    source, out_dir = sys.argv[1], sys.argv[2]
    report = {name: encode(name, spec, source, out_dir) for name, spec in CLIPS.items()}
    print(json.dumps(report, indent=2))
