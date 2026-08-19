#!/usr/bin/env python3
"""Remove the border-connected solid background from the SleepDown icon.

The script samples the four corner colours, floods only matching pixels that are
connected to the canvas edge, and feathers the resulting alpha mask.  Limiting
the key to the edge-connected region protects dark details inside the artwork.
"""

from __future__ import annotations

import argparse
from collections import deque
from pathlib import Path

from PIL import Image, ImageFilter


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="Source PNG/JPEG icon")
    parser.add_argument("output", type=Path, help="Transparent PNG output")
    parser.add_argument(
        "--threshold",
        type=int,
        default=90,
        help="Maximum RGB distance from a corner key colour (default: 90)",
    )
    parser.add_argument(
        "--feather",
        type=float,
        default=1.1,
        help="Alpha edge feather radius in pixels (default: 1.1)",
    )
    parser.add_argument(
        "--max-size",
        type=int,
        default=0,
        help="Optionally resize the longest edge before saving",
    )
    parser.add_argument(
        "--quality",
        type=int,
        default=88,
        help="WebP quality from 0 to 100 (default: 88)",
    )
    return parser.parse_args()


def key_distance_sq(pixel: tuple[int, int, int, int], keys: list[tuple[int, int, int]]) -> int:
    r, g, b, _ = pixel
    return min((r - kr) ** 2 + (g - kg) ** 2 + (b - kb) ** 2 for kr, kg, kb in keys)


def main() -> None:
    args = parse_args()
    source = Image.open(args.input).convert("RGBA")
    width, height = source.size
    pixels = source.load()

    sample_points = (
        (0, 0),
        (width - 1, 0),
        (0, height - 1),
        (width - 1, height - 1),
    )
    keys = [pixels[x, y][:3] for x, y in sample_points]
    limit = args.threshold * args.threshold

    keyed = bytearray(width * height)
    queued = bytearray(width * height)
    queue: deque[tuple[int, int]] = deque()

    def enqueue(x: int, y: int) -> None:
        index = y * width + x
        if queued[index]:
            return
        queued[index] = 1
        queue.append((x, y))

    for x in range(width):
        enqueue(x, 0)
        enqueue(x, height - 1)
    for y in range(1, height - 1):
        enqueue(0, y)
        enqueue(width - 1, y)

    while queue:
        x, y = queue.popleft()
        index = y * width + x
        if key_distance_sq(pixels[x, y], keys) > limit:
            continue
        keyed[index] = 255
        if x:
            enqueue(x - 1, y)
        if x + 1 < width:
            enqueue(x + 1, y)
        if y:
            enqueue(x, y - 1)
        if y + 1 < height:
            enqueue(x, y + 1)

    matte = Image.frombytes("L", source.size, bytes(keyed))
    if args.feather > 0:
        matte = matte.filter(ImageFilter.GaussianBlur(args.feather))

    original_alpha = source.getchannel("A")
    alpha = Image.eval(matte, lambda value: 255 - value)
    alpha = Image.composite(original_alpha, Image.new("L", source.size, 0), alpha)
    source.putalpha(alpha)

    if args.max_size and max(source.size) > args.max_size:
        source.thumbnail((args.max_size, args.max_size), Image.Resampling.LANCZOS)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    if args.output.suffix.lower() == ".webp":
        source.save(args.output, "WEBP", quality=args.quality, method=6, exact=True)
    else:
        source.save(args.output, "PNG", optimize=True)
    print(f"saved {args.output} ({source.width}x{source.height}, RGBA)")


if __name__ == "__main__":
    main()
