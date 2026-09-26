"""Downscale generated block art into 32x32 voxel-style atlas tiles."""

from __future__ import annotations

from pathlib import Path

from PIL import Image

SRC = Path(
    r"C:\Users\garag\.cursor\projects\c-Users-garag-OneDrive-Documents-Opencraft4\assets"
)
DST = Path(r"c:\Users\garag\OneDrive\Documents\Opencraft4\src\main\resources\textures\blocks")

NAMES = [
    "grass_top",
    "grass_side",
    "dirt",
    "stone",
    "sand",
    "wood_top",
    "wood_side",
    "leaves",
    "water",
]


def to_tile(img: Image.Image, name: str) -> Image.Image:
    img = img.convert("RGBA")
    width, height = img.size
    side = min(width, height)
    left = (width - side) // 2
    top = (height - side) // 2
    img = img.crop((left, top, left + side, top + side))
    tile = img.resize((32, 32), Image.Resampling.BOX)
    # Quantize RGB only — RGBA quantize is limited to Fast Octree.
    rgb = tile.convert("RGB").quantize(colors=24, method=Image.Quantize.MEDIANCUT)
    tile = rgb.convert("RGBA")
    pixels = tile.load()
    if name == "water":
        for y in range(32):
            for x in range(32):
                r, g, b, _ = pixels[x, y]
                pixels[x, y] = (r, g, b, 185)
    elif name == "leaves":
        for y in range(32):
            for x in range(32):
                r, g, b, _ = pixels[x, y]
                if r > 200 and g > 210 and b > 200:
                    pixels[x, y] = (r, g, b, 0)
                else:
                    pixels[x, y] = (r, g, b, 230)
    return tile


def main() -> None:
    DST.mkdir(parents=True, exist_ok=True)
    for name in NAMES:
        path = SRC / f"{name}.png"
        if not path.exists():
            print(f"missing {path}")
            continue
        out = to_tile(Image.open(path), name)
        out_path = DST / f"{name}.png"
        out.save(out_path, optimize=True)
        print(f"wrote {out_path} {out.size}")


if __name__ == "__main__":
    main()
