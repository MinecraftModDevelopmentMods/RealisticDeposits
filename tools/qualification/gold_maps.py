"""Draw labelled gold sections from the exact packaged-server measurements."""

import argparse
import json
from pathlib import Path

from PIL import Image, ImageDraw


COLOURS = {
    "minecraft:air": "#e5eff5", "minecraft:stone": "#8d9091", "minecraft:bedrock": "#41464b",
    "minecraft:gold_ore": "#e3b431", "realisticdeposits:gold_gravel": "#f0c849",
    "minecraft:water": "#5592c8", "minecraft:flowing_water": "#5592c8",
    "minecraft:grass": "#6e974f", "minecraft:dirt": "#9c795a", "minecraft:sand": "#dac995",
    "minecraft:gravel": "#b1a69c", "minecraft:sandstone": "#cdbd87",
    "minecraft:coal_ore": "#4a4b49", "minecraft:iron_ore": "#af8e76",
    "minecraft:redstone_ore": "#a44c4c", "minecraft:diamond_ore": "#54bfc0",
    "minecraft:leaves": "#6c8e54", "minecraft:log": "#81634b",
    "minecraft:cobblestone": "#70767c", "minecraft:mossy_cobblestone": "#6f846c",
    "minecraft:chest": "#ab8c51",
    "minecraft:lava": "#df7839", "minecraft:flowing_lava": "#df7839",
    "mineralogy:slate": "#555d70", "mineralogy:phyllite": "#6c7372",
    "mineralogy:schist": "#7b8377", "mineralogy:amphibolite": "#414f48",
    "mineralogy:gneiss": "#a3a398", "mineralogy:shale": "#846f5a",
    "mineralogy:siltstone": "#b09b7c", "mineralogy:limestone": "#b8c8ac",
    "mineralogy:basalt": "#505252", "mineralogy:dolomite": "#b8aa96",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--sample", default="first")
    parser.add_argument("--placer", action="store_true")
    parser.add_argument("--title", default="Gold veins and their surrounding rock")
    args = parser.parse_args()
    assert not args.output.exists(), "Keep the earlier map and choose another filename"

    report = json.loads(args.report.read_text(encoding="utf-8"))
    sample = report if args.placer else report[args.sample]
    cells = sample["placer_section" if args.placer else "section"]
    assert cells, "The measured section is empty"
    z = sample["rich_gravel_samples"][0]["z"] if args.placer else sample["centre_z"]
    left_x, right_x = min(cell[0] for cell in cells), max(cell[0] for cell in cells)
    bottom, top = min(cell[1] for cell in cells), max(cell[1] for cell in cells)
    used = sorted({cell[2] for cell in cells})
    scale = 5 if not args.placer else 10
    width = max(780, (right_x - left_x + 1) * scale + 100)
    height = (top - bottom + 1) * scale + 150 + 20 * ((len(used) + 2) // 3)
    image = Image.new("RGB", (width, height), "#f9faf8")
    draw = ImageDraw.Draw(image)
    draw.text((60, 15), args.title, fill="#222b31")
    draw.text((60, 33), f"Vertical slice at Z {z}; X and Y are world coordinates", fill="#3f4a50")

    for x, y, block in cells:
        px, py = 60 + (x - left_x) * scale, 60 + (top - y) * scale
        draw.rectangle((px, py, px + scale - 1, py + scale - 1), fill=COLOURS.get(block, "#a09a93"))
    for y in range((bottom // 10 + 1) * 10, top + 1, 10 if args.placer else 20):
        draw.text((10, 58 + (top - y) * scale), f"Y {y}", fill="#222b31")
    axis_y = 65 + (top - bottom + 1) * scale
    for x in (left_x, (left_x + right_x) // 2, right_x):
        draw.text((50 + (x - left_x) * scale, axis_y), str(x), fill="#222b31")
    for index, block in enumerate(used):
        px, py = 15 + index % 3 * 250, axis_y + 30 + index // 3 * 20
        draw.rectangle((px, py, px + 12, py + 12), fill=COLOURS.get(block, "#a09a93"))
        draw.text((px + 18, py), block.split(":")[1].replace("_", " "), fill="#222b31")
    draw.text((15, height - 25), "Measured blocks, not a schematic. Yellow marks gold ore or gold-bearing gravel.", fill="#3f4a50")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    image.save(args.output)


if __name__ == "__main__":
    main()
