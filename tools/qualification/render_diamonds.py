"""Draw a vertical block section from the diamond server probe's JSON report."""

import argparse
import json
from pathlib import Path

from PIL import Image, ImageDraw


COLOURS = {
    "minecraft:air": "#202d3c",
    "minecraft:water": "#3b6aa7",
    "minecraft:flowing_water": "#3b6aa7",
    "minecraft:stone": "#777b80",
    "minecraft:bedrock": "#303134",
    "minecraft:grass": "#779848",
    "minecraft:dirt": "#806344",
    "minecraft:sand": "#c7bc84",
    "minecraft:gravel": "#9d9790",
    "minecraft:diamond_ore": "#35e0dc",
    "realisticdeposits:diamond_gravel": "#e6ae49",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--sample", default="first")
    args = parser.parse_args()
    assert not args.output.exists(), "Keep the existing map and choose another output name"
    sample = json.loads(args.report.read_text())[args.sample]
    cells = sample["section"]
    minimum = min(cell[0] for cell in cells)
    maximum = max(cell[0] for cell in cells)
    top = max(cell[1] for cell in cells)
    scale = 5
    image = Image.new("RGB", ((maximum - minimum + 1) * scale + 80, (top + 1) * scale + 100), "#17212c")
    draw = ImageDraw.Draw(image)
    draw.text((18, 12), f"Diamond section: Z {sample['centre_z']} | X {minimum}..{maximum}", fill="white")
    for x, y, block in cells:
        px = 48 + (x - minimum) * scale
        py = 42 + (top - y) * scale
        draw.rectangle((px, py, px + scale - 1, py + scale - 1), fill=COLOURS.get(block, "#68635b"))
    for y in range(0, top + 1, 20):
        draw.text((12, 42 + (top - y) * scale), str(y), fill="white")
    draw.text((18, image.height - 35), "Cyan: diamond ore | Gold: rich gravel | Other colours: existing terrain", fill="white")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    image.save(args.output)


if __name__ == "__main__":
    main()
