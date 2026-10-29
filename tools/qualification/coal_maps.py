"""Draw labelled coalfield cross-sections from the packaged probe's saved reports."""

import argparse
import json
from pathlib import Path

from PIL import Image, ImageDraw


COLOURS = {
    "minecraft:air": "#eff5f8", "minecraft:coal_ore": "#101820", "minecraft:stone": "#96938e",
    "minecraft:water": "#6086bf", "minecraft:flowing_water": "#6086bf",
    "minecraft:lava": "#f08732", "minecraft:flowing_lava": "#f08732",
    "minecraft:sandstone": "#dbc898", "minecraft:grass": "#78964e", "minecraft:dirt": "#957156",
    "mineralogy:shale": "#71645d", "mineralogy:siltstone": "#aa866d",
    "mineralogy:conglomerate": "#8b7965", "mineralogy:limestone": "#bbc5aa",
    "mineralogy:dolomite": "#bdafa4", "mineralogy:rock_salt": "#d9ccd3",
    "mineralogy:schist": "#747e7a", "mineralogy:slate": "#56616b", "mineralogy:basalt": "#45454a",
}


def draw(report_path, output):
    report = json.loads(report_path.read_text(encoding="utf-8"))["first"]
    cells = report["section"]
    low = min(cell[0] for cell in cells)
    high = max(cell[0] for cell in cells)
    scale = 4
    left, top = 65, 55
    image = Image.new("RGB", ((high - low + 1) * scale + 95, 141 * scale + 180), "white")
    paint = ImageDraw.Draw(image)
    for x, y, block in cells:
        px, py = left + (x - low) * scale, top + (140 - y) * scale
        paint.rectangle((px, py, px + scale - 1, py + scale - 1), fill=COLOURS.get(block, "#aaa8a0"))
    paint.text((left, 12), f"{report_path.parent.name}: coal beds and surrounding rock", fill="black")
    paint.text((left, 29), f"X / height section at Z {report['centre_z']}; coal is black", fill="black")
    for y in (128, 96, 64, 32, 8):
        paint.text((12, top + (140 - y) * scale - 5), f"Y {y}", fill="black")
    for x in (low, report["centre_x"], high):
        paint.text((left + (x - low) * scale - 15, top + 141 * scale + 8), str(x), fill="black")
    used = sorted({cell[2] for cell in cells if cell[2] in COLOURS})
    for index, block in enumerate(used):
        x = 12 + (index % 3) * 195
        y = top + 141 * scale + 35 + (index // 3) * 17
        paint.rectangle((x, y, x + 10, y + 10), fill=COLOURS[block])
        paint.text((x + 15, y), block.split(":")[1], fill="black")
    image.save(output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    assert not args.output.exists(), "Keep earlier maps and choose a new directory"
    args.output.mkdir(parents=True)
    for report in sorted(args.input.glob("*/coal-first.json")):
        draw(report, args.output / (report.parent.name + ".png"))


if __name__ == "__main__":
    main()
