import argparse
from collections import deque
import math
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw


ROOT = Path(r"D:/Android studio/CourseSchedule/app/src/main/res")
LIGHT_SOURCE = ROOT.parent / "icon-artwork" / "ic_launcher_light_source.png"
DARK_SOURCE = ROOT.parent / "icon-artwork" / "ic_launcher_dark_source.png"
ICON_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}
# The widget renders this at 23-24 dp; 128 px still covers xxxhdpi without shipping
# two half-megabyte source bitmaps in every APK.
WIDGET_PREVIEW_SIZE = 128
WIDGET_CORNER_EXPONENT = 4.5
WIDGET_MASK_SUPERSAMPLING = 4


def remove_connected_black_corners(image: Image.Image) -> Image.Image:
    image = image.convert("RGBA")
    width, height = image.size
    pixels = image.load()
    queue = deque()
    seen = set()

    def is_outer_black(pixel: tuple[int, int, int, int]) -> bool:
        red, green, blue, alpha = pixel
        return alpha > 0 and red < 26 and green < 26 and blue < 26

    for x in range(width):
        queue.append((x, 0))
        queue.append((x, height - 1))
    for y in range(height):
        queue.append((0, y))
        queue.append((width - 1, y))

    while queue:
        x, y = queue.popleft()
        if (x, y) in seen or not (0 <= x < width and 0 <= y < height):
            continue
        if not is_outer_black(pixels[x, y]):
            continue
        seen.add((x, y))
        pixels[x, y] = (0, 0, 0, 0)
        queue.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))

    return image


def prepare_icon(source: Path) -> Image.Image:
    image = remove_connected_black_corners(Image.open(source))
    content_bounds = image.getchannel("A").getbbox()
    if content_bounds:
        image = image.crop(content_bounds)
    side = max(image.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.alpha_composite(image, ((side - image.width) // 2, (side - image.height) // 2))
    return square


def continuous_corner_mask(size: int) -> Image.Image:
    """Return an antialiased superellipse mask with continuous corner curvature."""
    scale = WIDGET_MASK_SUPERSAMPLING
    canvas_size = size * scale
    center = (canvas_size - 1) / 2
    radius = center
    power = 2 / WIDGET_CORNER_EXPONENT
    points = []
    for step in range(1440):
        angle = math.tau * step / 1440
        cosine = math.cos(angle)
        sine = math.sin(angle)
        x = center + radius * math.copysign(abs(cosine) ** power, cosine)
        y = center + radius * math.copysign(abs(sine) ** power, sine)
        points.append((x, y))
    mask = Image.new("L", (canvas_size, canvas_size), 0)
    ImageDraw.Draw(mask).polygon(points, fill=255)
    return mask.resize((size, size), Image.Resampling.LANCZOS)


def write_variant(source: Path, night: bool, launcher_icons: bool) -> None:
    icon = prepare_icon(source)
    qualifier = "night-" if night else ""
    if launcher_icons:
        for density, size in ICON_SIZES.items():
            output_dir = ROOT / f"mipmap-{qualifier}{density}"
            output_dir.mkdir(parents=True, exist_ok=True)
            resized = icon.resize((size, size), Image.Resampling.LANCZOS)
            resized.save(output_dir / "ic_launcher.png", optimize=True)
            resized.save(output_dir / "ic_launcher_round.png", optimize=True)

    preview_dir = ROOT / ("drawable-night" if night else "drawable")
    preview_dir.mkdir(parents=True, exist_ok=True)
    preview = icon.resize((WIDGET_PREVIEW_SIZE, WIDGET_PREVIEW_SIZE), Image.Resampling.LANCZOS)
    preview.putalpha(ImageChops.multiply(preview.getchannel("A"), continuous_corner_mask(WIDGET_PREVIEW_SIZE)))
    preview.save(
        preview_dir / "ic_launcher_preview.png",
        optimize=True,
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--widget-previews-only",
        action="store_true",
        help="Regenerate only the widget header icons, leaving launcher resources untouched.",
    )
    args = parser.parse_args()
    launcher_icons = not args.widget_previews_only
    write_variant(LIGHT_SOURCE, night=False, launcher_icons=launcher_icons)
    write_variant(DARK_SOURCE, night=True, launcher_icons=launcher_icons)
    print("Generated continuous-corner widget icons." if args.widget_previews_only else "Generated launcher resources.")


if __name__ == "__main__":
    main()
