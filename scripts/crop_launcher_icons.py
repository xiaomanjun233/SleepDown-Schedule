"""Crop the four approved originals; never infer artwork bounds from colors.

Run from any directory with Pillow installed. Original paintings stay unchanged.
Coordinates are normalized to the 1254px originals, shared by light/dark pairs.
Only fixed light/dark mipmaps are generated. Follow-mode mipmap[-night] bitmap
XMLs and the notification aliases in values/app_icon_aliases.xml share them.
"""
from pathlib import Path
import math
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def prepare(filename, box):
    with Image.open(ROOT / filename) as source:
        assert source.width == source.height, "Original must remain square"
        bounds = tuple(round(v * source.width / 1254) for v in box)
        icon = source.convert("RGBA").crop(bounds)
    # Transparent, antialiased continuous corners, including the dark artwork:
    # never flood-fill near-white hair or dark blue interior details.
    side = icon.width
    mask = Image.new("L", (side, side))
    center = (side - 1) / 2
    points = []
    for i in range(1440):
        angle = i * math.tau / 1440
        c, s = math.cos(angle), math.sin(angle)
        points.append((center + center * math.copysign(abs(c) ** (2 / 4.5), c),
                       center + center * math.copysign(abs(s) ** (2 / 4.5), s)))
    ImageDraw.Draw(mask).polygon(points, fill=255)
    icon.putalpha(mask)
    return icon


def save(icon, directory, name, size):
    directory.mkdir(parents=True, exist_ok=True)
    icon.resize((size, size), Image.Resampling.LANCZOS).save(directory / (name + ".png"))


def main():
    cards = []
    for stem, files, box in (
        ("ic_launcher", ("简约_浅色.png", "简约_深色.png"), (85, 85, 1169, 1169)),
        ("ic_launcher_kanban", ("浅色模式_看板娘.png", "深色模式_看板娘.png"), (42, 38, 1212, 1208)),
    ):
        for dark, filename in enumerate(files):
            icon = prepare(filename, box)
            cards.append(icon)
            for density, size in SIZES.items():
                save(icon, RES / f"mipmap-{density}", stem + ("_dark" if dark else "_light"), size)
    preview = Image.new("RGB", (960, 288), "#888893")
    for index, icon in enumerate(cards):
        scaled = icon.resize((192, 192), Image.Resampling.LANCZOS)
        preview.paste(scaled, (index * 240 + 24, 24), scaled)
        small = icon.resize((48, 48), Image.Resampling.LANCZOS)
        preview.paste(small, (index * 240 + 96, 228), small)
    destination = ROOT / "tmp/icon-crop-review.png"
    destination.parent.mkdir(exist_ok=True)
    preview.save(destination)
    print("Generated four variants at five densities and review sheet:", destination)


if __name__ == "__main__":
    main()
