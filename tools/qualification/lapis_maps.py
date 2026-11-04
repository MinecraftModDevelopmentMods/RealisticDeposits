"""Draw a labelled cross-section from measured lapis-test blocks."""

import argparse
import json
from pathlib import Path

from PIL import Image, ImageDraw
from gold_maps import COLOURS


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--sample", default="first")
    parser.add_argument("--title", default="Lapis lenses and their surrounding rock")
    args = parser.parse_args()
    assert not args.output.exists(), "Keep previous maps"
    sample = json.loads(args.report.read_text())[args.sample]
    along_strike = "strike_section" in sample
    cells = sample["strike_section" if along_strike else "section"]
    assert cells, "The measured section is empty"
    palette = dict(COLOURS, **{"minecraft:lapis_ore": "#255de0", "mineralogy:marble": "#e4e0d1"})
    left, right = min(cell[0] for cell in cells), max(cell[0] for cell in cells)
    bottom, top = min(cell[1] for cell in cells), max(cell[1] for cell in cells)
    blocks = sorted({cell[2] for cell in cells})
    scale = 5
    width = max(840, (right - left + 1) * scale + 100)
    height = (top - bottom + 1) * scale + 155 + 20 * ((len(blocks) + 2) // 3)
    image = Image.new("RGB", (width, height), "#f9faf8")
    draw = ImageDraw.Draw(image)
    draw.text((60, 15), args.title, fill="#222b31")
    caption = (f"Along the principal lens near {sample['center_x']}, {sample['center_z']}; distance in blocks"
               if along_strike else f"Measured vertical section at Z {sample['center_z']}; world coordinates")
    draw.text((60, 33), caption, fill="#3f4a50")

    for x, y, block, _host, *_world in cells:
        px, py = 60 + (x - left) * scale, 60 + (top - y) * scale
        draw.rectangle((px, py, px + scale - 1, py + scale - 1), fill=palette.get(block, "#a09a93"))

    for y in range((bottom // 20 + 1) * 20, top + 1, 20):
        draw.text((10, 58 + (top - y) * scale), f"Y {y}", fill="#222b31")
    axis = 65 + (top - bottom + 1) * scale
    for x in (left, (left + right) // 2, right):
        draw.text((50 + (x - left) * scale, axis), str(x), fill="#222b31")
    for index, block in enumerate(blocks):
        px, py = 15 + index % 3 * 270, axis + 30 + index // 3 * 20
        draw.rectangle((px, py, px + 12, py + 12), fill=palette.get(block, "#a09a93"))
        draw.text((px + 18, py), block.split(":")[1].replace("_", " "), fill="#222b31")
    draw.text((15, height - 25), "Blue marks lapis ore. This is measured terrain, not a schematic.", fill="#3f4a50")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    image.save(args.output)


if __name__ == "__main__":
    main()
