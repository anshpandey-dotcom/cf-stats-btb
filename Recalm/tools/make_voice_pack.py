import argparse
import json
import tempfile
import zipfile
from pathlib import Path

import onnx


def add_metadata(model_path: Path, config: dict, out_path: Path) -> None:
    model = onnx.load(str(model_path))
    meta = {
        "model_type": "vits",
        "comment": "piper",
        "language": config.get("language", {}).get("name_english", "Hindi"),
        "voice": config["espeak"]["voice"],
        "has_espeak": 1,
        "n_speakers": config.get("num_speakers", 1),
        "sample_rate": config["audio"]["sample_rate"],
    }
    while len(model.metadata_props):
        model.metadata_props.pop()
    for key, value in meta.items():
        entry = model.metadata_props.add()
        entry.key = key
        entry.value = str(value)
    onnx.save(model, str(out_path))


def write_tokens(config: dict, out_path: Path) -> None:
    lines = []
    for symbol, ids in config["phoneme_id_map"].items():
        lines.append(f"{symbol} {ids[0]}")
    out_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description="Turn a trained Piper voice into a Recalm voice pack")
    parser.add_argument("model", type=Path, help="Path to the Piper .onnx file")
    parser.add_argument("--config", type=Path, help="Path to the .onnx.json file (defaults to <model>.json)")
    parser.add_argument("--out", type=Path, default=Path("my_voice.zip"))
    args = parser.parse_args()

    config_path = args.config or Path(str(args.model) + ".json")
    config = json.loads(config_path.read_text(encoding="utf-8"))

    with tempfile.TemporaryDirectory() as tmp:
        folder = Path(tmp) / "voice"
        folder.mkdir()
        add_metadata(args.model, config, folder / "model.onnx")
        write_tokens(config, folder / "tokens.txt")
        with zipfile.ZipFile(args.out, "w", zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(folder.rglob("*")):
                if path.is_file():
                    archive.write(path, path.relative_to(folder.parent))
    print(f"Saved {args.out}. Import it in Recalm: Voices, Your own voice, Import voice.")


if __name__ == "__main__":
    main()
