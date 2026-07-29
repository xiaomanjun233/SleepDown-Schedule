from collections import deque
from pathlib import Path
from PIL import Image


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


def write_variant(source: Path, night: bool) -> None:
    icon = prepare_icon(source)
    qualifier = "night-" if night else ""
    for density, size in ICON_SIZES.items():
        output_dir = ROOT / f"mipmap-{qualifier}{density}"
        output_dir.mkdir(parents=True, exist_ok=True)
        resized = icon.resize((size, size), Image.Resampling.LANCZOS)
        resized.save(output_dir / "ic_launcher.png", optimize=True)
        resized.save(output_dir / "ic_launcher_round.png", optimize=True)

    preview_dir = ROOT / ("drawable-night" if night else "drawable")
    preview_dir.mkdir(parents=True, exist_ok=True)
    icon.resize((512, 512), Image.Resampling.LANCZOS).save(
        preview_dir / "ic_launcher_preview.png",
        optimize=True,
    )


write_variant(LIGHT_SOURCE, night=False)
write_variant(DARK_SOURCE, night=True)
print("Generated light and night launcher icon resources.")
