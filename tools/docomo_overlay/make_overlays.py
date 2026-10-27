import os
import glob
import random
import math
from PIL import Image

CANVAS_WIDTH = 480
CANVAS_HEIGHT = 800

# 12x12 at 3x makes 36x36 sprites
SCALE_FACTOR = 2

INPUT_DIR = "./assets"
OUTPUT_DIR = "./output"
os.makedirs(OUTPUT_DIR, exist_ok=True)

#Mochibun's pastel palette
PASTEL_PALETTE = [
    "#F2A7BF",  # Sweet Strawberry Pink
    "#C7A4F5",  # Lavender Purple
    "#90D5FB",  # Baby Sky Blue
    "#86E8BA",  # Mint Green
    "#FFB896",  # Peach
    "#FFE066",  # Butter Yellow
]


def load_and_scale_sprites(folder: str, scale: int = SCALE_FACTOR) -> list[Image.Image]:
    sprites = []

    patterns = [os.path.join(folder, "*.png"), os.path.join(folder, "*.PNG")]
    file_paths = []

    for pattern in patterns:
        file_paths.extend(glob.glob(pattern))

    if not file_paths:
        raise FileNotFoundError(f"No PNG sprites found inside '{folder}'. Place your emojis there first!")

    for path in sorted(file_paths):
        # enforce rgba
        img = Image.open(path).convert("RGBA")

        if scale > 1:
            target_w = img.width * scale
            target_h = img.height * scale
            img = img.resize((target_w, target_h), resample=Image.Resampling.NEAREST)

        sprites.append(img)

    print(f"Loaded and scaled {len(sprites)} sprite(s) to {sprites[0].width}x{sprites[0].height}px.")
    return sprites

def tint_sprite(sprite: Image.Image, hex_color: str) -> Image.Image:
    # convert hex to rgb
    clean_hex = hex_color.lstrip("#")
    r, g, b = tuple(int(clean_hex[i:i+2], 16) for i in (0, 2, 4))

    alpha_mask = sprite.split()[3]

    tinted = Image.new("RGBA", sprite.size, (r, g, b, 255))

    # reapply original alpha channel
    tinted.putalpha(alpha_mask)
    return tinted

def pattern_scatter(sprites: list[Image.Image], count: int = 24, candidates: int = 12) -> Image.Image:
    canvas = Image.new("RGBA", (CANVAS_WIDTH, CANVAS_HEIGHT), (0, 0, 0, 0))
    placed_positions = []

    for _ in range(count):
        sprite = random.choice(sprites)
        color = random.choice(PASTEL_PALETTE)
        tinted = tint_sprite(sprite, color)

        sw, sh = tinted.size

        margin_x = 16
        margin_y = 24
        max_x = CANVAS_WIDTH - sw - margin_x
        max_y = CANVAS_HEIGHT - sh - margin_y

        best_pos = None
        best_dist = -1

        # test spots and pick furthest away from existing ones
        sample_pool = candidates if placed_positions else 1
        for _ in range(sample_pool):
            cx = random.randint(margin_x, max(margin_x, max_x))
            cy = random.randint(margin_y, max(margin_y, max_y))

            if not placed_positions:
                best_pos = (cx, cy)
                break

            min_dist = min(math.hypot(cx - px, cy - py) for px, py in placed_positions)
            if min_dist > best_dist:
                best_dist = min_dist
                best_pos = (cx, cy)

        placed_positions.append(best_pos)
        canvas.alpha_composite(tinted, dest=best_pos)

    return canvas

def pattern_staggered_grid(sprites: list[Image.Image], cell_w: int = 64, cell_h: int = 64) -> Image.Image:
    canvas = Image.new("RGBA", (CANVAS_WIDTH, CANVAS_HEIGHT), (0, 0, 0, 0))
    cols = CANVAS_WIDTH // cell_w + 2
    rows = CANVAS_HEIGHT // cell_h + 2

    for r in range(rows):
        offset_x = (cell_w // 2) if (r % 2 == 1) else 0

        for c in range(-1, cols):
            sprite = random.choice(sprites)
            color = random.choice(PASTEL_PALETTE)
            tinted = tint_sprite(sprite, color)

            x = (c * cell_w) + offset_x + (cell_w - tinted.width) // 2
            y = (r * cell_h) + (cell_h - tinted.height) // 2

            if -tinted.width < x < CANVAS_WIDTH and -tinted.height < CANVAS_HEIGHT:
                canvas.alpha_composite(tinted, dest=(x, y))

    return canvas

# main execution
if __name__ == "__main__":
    print(f"Loading sprites from '{INPUT_DIR}'...")
    assets = load_and_scale_sprites(INPUT_DIR, scale=SCALE_FACTOR)

    # staggered grid
    print("Generating staggered grid overlays...")
    for i in range(1, 5):
        grid_img = pattern_staggered_grid(assets, cell_w=48, cell_h=48)
        out_path = os.path.join(OUTPUT_DIR, f"wallpaper_overlay_{i}.png")
        grid_img.save(out_path, format="PNG")
        print(f" -> Saved {out_path}")

    print(f"\nDone! Overlays are saved in '{OUTPUT_DIR}' with full transparency.")
