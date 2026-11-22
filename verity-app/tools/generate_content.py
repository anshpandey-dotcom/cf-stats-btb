import json
import random
import shutil
import subprocess
import tempfile
import uuid
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
SOURCES = ROOT / "content-src"
AUTHOR = "FleetMc Studio"
NAMESPACE = "fmc"
ENGINE = "1.26.60"
LANGUAGES = ["en_US", "zh_CN", "ru_RU", "es_ES", "pt_BR"]
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

ADDONS = [
    {
        "id": "crimson_blades", "name": "Crimson Blades", "category": "Weapons", "kind": "addon",
        "min": "1.21.0", "max": "1.26.60",
        "palette": ["#2b0a12", "#7a1426", "#d93a4a", "#ffb020"],
        "description": "A forge-born arsenal of red-steel swords, scythes and daggers. Every blade has a unique swing arc, a bleeding particle trail and a charged heavy attack.",
        "item": {"en_US": "Crimson Blade", "zh_CN": "绯红之刃", "ru_RU": "Багровый клинок", "es_ES": "Hoja carmesí", "pt_BR": "Lâmina carmesim"},
    },
    {
        "id": "shadow_stalker", "name": "Shadow Stalker", "category": "Horror", "kind": "addon",
        "min": "1.21.0", "max": "1.26.60",
        "palette": ["#07070d", "#1c1830", "#5b3fa8", "#9be564"],
        "description": "Something follows you through the dark. A silent hunter that mimics sounds, avoids torchlight and only moves when you look away.",
        "item": {"en_US": "Stalker Shard", "zh_CN": "潜行者碎片", "ru_RU": "Осколок сталкера", "es_ES": "Fragmento acechador", "pt_BR": "Fragmento espreitador"},
    },
    {
        "id": "lucky_geodes", "name": "Lucky Geodes", "category": "Tools", "kind": "addon",
        "min": "1.20.80", "max": "1.26.60",
        "palette": ["#0c2233", "#1b5e7a", "#4fd1c5", "#ffd166"],
        "description": "Crack open glittering geodes while mining. Each one rolls a loot table of gems, enchanted pickaxes and rare crystal tools.",
        "item": {"en_US": "Lucky Geode", "zh_CN": "幸运晶洞", "ru_RU": "Счастливая жеода", "es_ES": "Geoda afortunada", "pt_BR": "Geodo da sorte"},
    },
    {
        "id": "modern_living", "name": "Modern Living", "category": "Decor", "kind": "addon",
        "min": "1.21.0", "max": "1.26.60",
        "palette": ["#1a1f24", "#3d4a52", "#8fb8c4", "#f2c14e"],
        "description": "Sofas, lamps, kitchens and bathrooms with a clean modern look. Fully rotatable furniture that snaps to the block grid.",
        "item": {"en_US": "Designer Lamp", "zh_CN": "设计师台灯", "ru_RU": "Дизайнерская лампа", "es_ES": "Lámpara de diseño", "pt_BR": "Luminária de design"},
    },
    {
        "id": "sky_riders", "name": "Sky Riders", "category": "Mobs", "kind": "addon",
        "min": "1.21.0", "max": "1.26.60",
        "palette": ["#0b1d4d", "#2f6fdd", "#9ad1ff", "#ffe08a"],
        "description": "Tame winged creatures and soar above the clouds. Saddle up wyverns, manta gliders and storm birds, each with its own flight style.",
        "item": {"en_US": "Sky Saddle", "zh_CN": "天空马鞍", "ru_RU": "Небесное седло", "es_ES": "Silla celeste", "pt_BR": "Sela celeste"},
    },
    {
        "id": "hunter_armory", "name": "Hunter Armory", "category": "Weapons", "kind": "addon",
        "min": "1.20.80", "max": "1.26.60",
        "palette": ["#14210f", "#3b5d2a", "#8fbf5a", "#e0a030"],
        "description": "Crossbows, traps and ranged gear for the dedicated monster hunter. Craft ammunition types and track prey across the wild.",
        "item": {"en_US": "Hunter Bolt", "zh_CN": "猎人弩箭", "ru_RU": "Болт охотника", "es_ES": "Virote de cazador", "pt_BR": "Virote de caçador"},
    },
    {
        "id": "hollow_manor", "name": "Hollow Manor", "category": "Horror", "kind": "addon",
        "min": "1.21.0", "max": "1.26.60",
        "palette": ["#0d0b0b", "#3a2a22", "#a0522d", "#e8d9a0"],
        "description": "An abandoned mansion that rearranges itself when you are not looking. Collect keys, read diaries and survive the night inside.",
        "item": {"en_US": "Manor Key", "zh_CN": "庄园钥匙", "ru_RU": "Ключ от особняка", "es_ES": "Llave de la mansión", "pt_BR": "Chave da mansão"},
    },
    {
        "id": "pocket_mechs", "name": "Pocket Mechs", "category": "Mobs", "kind": "addon",
        "min": "1.21.0", "max": "1.26.60",
        "palette": ["#101820", "#2d4256", "#5ec8ff", "#ff7a3d"],
        "description": "Build, upgrade and command small battle robots. Swap modules, set behaviours and send them to fight beside you.",
        "item": {"en_US": "Mech Core", "zh_CN": "机甲核心", "ru_RU": "Ядро меха", "es_ES": "Núcleo mecánico", "pt_BR": "Núcleo mecânico"},
    },
    {
        "id": "cozy_cabin", "name": "Cozy Cabin Decor", "category": "Decor", "kind": "pack",
        "min": "1.20.80", "max": "1.26.60",
        "palette": ["#2a1a10", "#6b4423", "#d9a066", "#ffcf66"],
        "description": "A warm resource pack of rustic wood, soft lantern glow and woollen textures that turns every build into a mountain retreat.",
        "item": None,
    },
    {
        "id": "quick_haul", "name": "Quick Haul Tools", "category": "Utility", "kind": "pack",
        "min": "1.20.80", "max": "1.26.60",
        "palette": ["#101a14", "#254a35", "#4ade80", "#ffb020"],
        "description": "Quality-of-life interface and inventory polish. Clearer hotbar, tidier item names and compact tool tips for fast play.",
        "item": None,
    },
]


def hex_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient(size, top, bottom):
    width, height = size
    image = Image.new("RGB", size)
    draw = ImageDraw.Draw(image)
    for y in range(height):
        draw.line([(0, y), (width, y)], fill=mix(top, bottom, y / max(height - 1, 1)))
    return image


def sprite(seed, cells, color, accent):
    rng = random.Random(seed)
    half = (cells + 1) // 2
    grid = [[rng.random() < 0.55 for _ in range(half)] for _ in range(cells)]
    image = Image.new("RGBA", (cells, cells), (0, 0, 0, 0))
    for y in range(cells):
        for x in range(cells):
            if grid[y][x if x < half else cells - 1 - x]:
                shade = mix(color, accent, 0.25 if (x + y) % 5 == 0 else 0.0)
                image.putpixel((x, y), shade + (255,))
    eye_y = cells // 3
    for x in (half - 2, cells - half + 1):
        image.putpixel((x, eye_y), accent + (255,))
    return image


def paste_sprite(canvas, art, box, size):
    scaled = art.resize((size, size), Image.NEAREST)
    shadow = Image.new("RGBA", scaled.size, (0, 0, 0, 0))
    mask = scaled.split()[3].point(lambda v: 90 if v else 0)
    shadow.putalpha(mask)
    canvas.paste(shadow, (box[0] + 6, box[1] + 6), shadow)
    canvas.paste(scaled, box, scaled)


def terrain(canvas, rng, base_y, block, color, accent):
    draw = ImageDraw.Draw(canvas)
    width, height = canvas.size
    level = base_y // block
    for col in range(width // block + 1):
        level = max(2, min(height // block - 2, level + rng.choice([-1, 0, 0, 0, 1])))
        for row in range(level, height // block + 1):
            shade = mix(color, (0, 0, 0), 0.12 * (row - level) % 0.5)
            if row == level:
                shade = mix(accent, color, 0.55)
            x, y = col * block, row * block
            draw.rectangle([x, y, x + block - 1, y + block - 1], fill=shade)
            draw.rectangle([x, y, x + block - 1, y + block - 1], outline=mix(shade, (0, 0, 0), 0.25))


def label(canvas, text, xy, size, fill, anchor="la"):
    font = ImageFont.truetype(FONT, size)
    draw = ImageDraw.Draw(canvas)
    draw.text((xy[0] + 3, xy[1] + 3), text, font=font, fill=(0, 0, 0), anchor=anchor)
    draw.text(xy, text, font=font, fill=fill, anchor=anchor)


def preview(addon, index):
    palette = [hex_rgb(c) for c in addon["palette"]]
    rng = random.Random(f"{addon['id']}-{index}")
    width, height = 960, 540
    canvas = gradient((width, height), palette[0], palette[1]).convert("RGBA")
    draw = ImageDraw.Draw(canvas)
    if index == 0:
        draw.ellipse([width - 250, 60, width - 130, 180], fill=mix(palette[3], palette[1], 0.2))
        terrain(canvas, rng, 400, 40, palette[1], palette[2])
        art = sprite(addon["id"], 9, palette[2], palette[3])
        paste_sprite(canvas, art, (width // 2 - 140, 120), 280)
        label(canvas, addon["name"].upper(), (width // 2, 480), 54, (255, 255, 255), "mm")
    elif index == 1:
        for _ in range(26):
            x, y = rng.randrange(0, width, 60), rng.randrange(0, height, 60)
            draw.rectangle([x, y, x + 59, y + 59], outline=mix(palette[1], palette[2], 0.35), width=2)
        for slot in range(3):
            art = sprite(f"{addon['id']}{slot}", 7, mix(palette[2], palette[3], slot / 2), palette[3])
            paste_sprite(canvas, art, (110 + slot * 280, 150), 210)
        label(canvas, addon["category"].upper(), (60, 50), 34, palette[3])
        label(canvas, "Included in this pack", (width // 2, 470), 38, (255, 255, 255), "mm")
    else:
        for _ in range(60):
            x, y = rng.randrange(width), rng.randrange(0, height // 2)
            size = rng.choice([2, 3, 4])
            draw.rectangle([x, y, x + size, y + size], fill=(255, 255, 255))
        draw.ellipse([90, 70, 190, 170], fill=mix(palette[3], (255, 255, 255), 0.3))
        terrain(canvas, rng, 360, 30, mix(palette[1], palette[0], 0.4), palette[2])
        art = sprite(addon["id"] + "night", 11, palette[3], palette[2])
        paste_sprite(canvas, art, (560, 150), 220)
        label(canvas, f"Minecraft {addon['min']}+", (60, 470), 38, (255, 255, 255))
    return canvas.convert("RGB").resize((640, 360), Image.LANCZOS)


def pack_icon(addon):
    palette = [hex_rgb(c) for c in addon["palette"]]
    canvas = gradient((256, 256), palette[1], palette[0]).convert("RGBA")
    art = sprite(addon["id"], 9, palette[2], palette[3])
    paste_sprite(canvas, art, (40, 40), 176)
    return canvas.convert("RGB")


def item_texture(addon):
    palette = [hex_rgb(c) for c in addon["palette"]]
    art = sprite(addon["id"] + "item", 8, palette[2], palette[3])
    return art.resize((16, 16), Image.NEAREST)


def manifest(name, description, pack_uuid, module_type, module_uuid, dependency=None):
    data = {
        "format_version": 3,
        "header": {
            "name": name,
            "description": description,
            "uuid": pack_uuid,
            "version": "1.0.0",
            "min_engine_version": ENGINE,
        },
        "modules": [{"type": module_type, "uuid": module_uuid, "version": "1.0.0"}],
        "metadata": {"authors": [AUTHOR], "product_type": "addon"},
    }
    if dependency:
        data["dependencies"] = [{"uuid": dependency, "version": "1.0.0"}]
    return data


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def build_resource(addon, folder, rp_uuid, bp_uuid):
    description = f"{addon['name']} resources by {AUTHOR}"
    write_json(folder / "manifest.json", manifest(
        f"{addon['name']} RP", description, rp_uuid, "resources", str(uuid.uuid4()), bp_uuid))
    pack_icon(addon).save(folder / "pack_icon.png")
    texts = folder / "texts"
    write_json(texts / "languages.json", LANGUAGES)
    for lang in LANGUAGES:
        lines = []
        if addon["item"]:
            lines.append(f"item.{NAMESPACE}:{addon['id']}_token={addon['item'][lang]}")
        (texts / f"{lang}.lang").write_text("\n".join(lines) + "\n", encoding="utf-8")
    if addon["item"]:
        texture_id = f"{NAMESPACE}_{addon['id']}_token"
        write_json(folder / "textures" / "item_texture.json", {
            "resource_pack_name": addon["id"],
            "texture_name": "atlas.items",
            "texture_data": {texture_id: {"textures": f"textures/items/{texture_id}"}},
        })
        target = folder / "textures" / "items"
        target.mkdir(parents=True, exist_ok=True)
        item_texture(addon).save(target / f"{texture_id}.png")


def build_behavior(addon, folder, bp_uuid, rp_uuid):
    write_json(folder / "manifest.json", manifest(
        f"{addon['name']} BP", f"{addon['name']} behaviors by {AUTHOR}",
        bp_uuid, "data", str(uuid.uuid4()), rp_uuid))
    pack_icon(addon).save(folder / "pack_icon.png")
    identifier = f"{NAMESPACE}:{addon['id']}_token"
    write_json(folder / "items" / f"{addon['id']}_token.json", {
        "format_version": "1.21.60",
        "minecraft:item": {
            "description": {"identifier": identifier, "menu_category": {"category": "items"}},
            "components": {
                "minecraft:icon": {"textures": {"default": f"{NAMESPACE}_{addon['id']}_token"}},
                "minecraft:max_stack_size": 16,
            },
        },
    })


def zip_folder(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        target.unlink()
    subprocess.run(["zip", "-r", "-X", "-q", str(target), "."], cwd=source, check=True)


def build_addon(addon):
    pascal = "".join(part.capitalize() for part in addon["id"].split("_"))
    source = SOURCES / pascal
    if source.exists():
        shutil.rmtree(source)
    out_dir = ASSETS / "addons" / addon["id"]
    if out_dir.exists():
        shutil.rmtree(out_dir)
    out_dir.mkdir(parents=True)
    if addon["kind"] == "addon":
        bp_uuid, rp_uuid = str(uuid.uuid4()), str(uuid.uuid4())
        build_behavior(addon, source / "BP", bp_uuid, rp_uuid)
        build_resource(addon, source / "RP", rp_uuid, bp_uuid)
        file_name = f"{pascal}.mcaddon"
        with tempfile.TemporaryDirectory() as staging:
            staging_path = Path(staging)
            shutil.copytree(source / "BP", staging_path / f"{pascal}_BP")
            shutil.copytree(source / "RP", staging_path / f"{pascal}_RP")
            zip_folder(staging_path, out_dir / file_name)
    else:
        build_resource_only(addon, source / "RP")
        file_name = f"{pascal}.mcpack"
        zip_folder(source / "RP", out_dir / file_name)
    for index in range(3):
        preview(addon, index).save(out_dir / f"preview_{index + 1}.webp", "WEBP", quality=80, method=6)
    return {
        "id": addon["id"],
        "name": addon["name"],
        "description": addon["description"],
        "category": addon["category"],
        "minVersion": addon["min"],
        "maxVersion": addon["max"],
        "file": file_name,
        "sizeBytes": (out_dir / file_name).stat().st_size,
        "previews": [f"preview_{n}.webp" for n in (1, 2, 3)],
    }


def build_resource_only(addon, folder):
    description = f"{addon['name']} by {AUTHOR}"
    write_json(folder / "manifest.json", manifest(
        addon["name"], description, str(uuid.uuid4()), "resources", str(uuid.uuid4())))
    pack_icon(addon).save(folder / "pack_icon.png")
    texts = folder / "texts"
    write_json(texts / "languages.json", LANGUAGES)
    for lang in LANGUAGES:
        (texts / f"{lang}.lang").write_text(f"pack.name={addon['name']}\n", encoding="utf-8")


def main():
    entries = [build_addon(addon) for addon in ADDONS]
    write_json(ASSETS / "catalog.json", entries)
    print(f"{len(entries)} add-ons written")


if __name__ == "__main__":
    main()
