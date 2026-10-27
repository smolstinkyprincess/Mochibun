import math
import os
import shutil
import zipfile
from PIL import Image, ImageDraw, ImageSequence

WIDTH, HEIGHT = 480, 800
FPS = 6  # Soft, cozy cadence
BG_COLOR = (255, 245, 248)       # Strawberry milk / cream (#FFF5F8)
LILAC_BACK = (222, 212, 240)      # Slightly deeper lavender for back cloud layer
LILAC_FRONT = (237, 231, 246)     # Pastel lilac for front puff layer (#EDE7F6)
DOT_INACTIVE = (225, 215, 235)    # Inactive dot
DOT_ACTIVE = (255, 148, 186)      # Sweet strawberry pink dot (#FF94BA)
TEXT_PLUM = (74, 46, 86)          # Deep plum (#4A2E56)

# Directories
ASSETS_DIR = "assets"
OUTPUT_DIR = "output"
PART0_DIR = os.path.join(OUTPUT_DIR, "part0")
PART1_DIR = os.path.join(OUTPUT_DIR, "part1")

os.makedirs(ASSETS_DIR, exist_ok=True)
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Setup workspace folders inside output/
for folder in [PART0_DIR, PART1_DIR]:
    if os.path.exists(folder):
        shutil.rmtree(folder)
    os.makedirs(folder)

def extract_and_scale_gif(filename: str, scale: int = 2) -> list[Image.Image]:
    """Extracts GIF frames, converts to RGBA, and scales crisp with Nearest Neighbor."""
    frames = []
    file_path = os.path.join(ASSETS_DIR, filename)
    with Image.open(file_path) as im:
        for frame in ImageSequence.Iterator(im):
            rgba = frame.convert("RGBA")
            target_size = (rgba.width * scale, rgba.height * scale)
            scaled = rgba.resize(target_size, resample=Image.Resampling.NEAREST)
            frames.append(scaled)
    return frames

def apply_alpha_fade(image: Image.Image, opacity: float) -> Image.Image:
    """Multiplies an RGBA image's existing alpha channel by a float (0.0 to 1.0)."""
    if opacity <= 0.0:
        return Image.new("RGBA", image.size, (0, 0, 0, 0))
    if opacity >= 1.0:
        return image.copy()

    r, g, b, a = image.split()
    # Scale each pixel's alpha value
    scaled_a = a.point(lambda p: int(p * opacity))
    faded = Image.merge("RGBA", (r, g, b, scaled_a))
    return faded

print("Extracting frames from assets/mochibunny_sleep_anim.gif...")
sleep_frames = extract_and_scale_gif("mochibunny_sleep_anim.gif", scale=2)

# ============================================================================
# LOGO SETUP
# ============================================================================
logo_img = None
logo_bx = 0
LOGO_Y = 100       # Y coordinate for the logo in the upper sky (tweak if needed)
LOGO_SCALE = 1    # Integer nearest-neighbor scale factor

logo_path = os.path.join(ASSETS_DIR, "logo.png")
if os.path.exists(logo_path):
    raw_logo = Image.open(logo_path).convert("RGBA")
    logo_size = (raw_logo.width * LOGO_SCALE, raw_logo.height * LOGO_SCALE)
    logo_img = raw_logo.resize(logo_size, resample=Image.Resampling.NEAREST)
    logo_bx = (WIDTH - logo_img.width) // 2

def draw_back_clouds(draw: ImageDraw.ImageDraw, y_base: int):
    """Draws background cloud shelf behind Mochibunny."""
    top_y = min(y_base + 35, HEIGHT)
    if top_y < HEIGHT:
        draw.rectangle([0, top_y, WIDTH, HEIGHT], fill=LILAC_BACK)
    # Staggered rear cloud puffs
    draw.ellipse([-20, y_base - 10, 160, y_base + 90], fill=LILAC_BACK)
    draw.ellipse([120, y_base - 25, 360, y_base + 95], fill=LILAC_BACK)
    draw.ellipse([320, y_base - 5, 500, y_base + 95], fill=LILAC_BACK)

def draw_front_clouds(draw: ImageDraw.ImageDraw, y_base: int):
    """Draws foreground cloud puffs overlapping the bottom of Mochibunny."""
    top_y = min(y_base + 45, HEIGHT)
    if top_y < HEIGHT:
        draw.rectangle([0, top_y, WIDTH, HEIGHT], fill=LILAC_FRONT)
    # Front scalloped puffs shifted horizontally to create depth
    draw.ellipse([-50, y_base, 110, y_base + 110], fill=LILAC_FRONT)
    draw.ellipse([60, y_base - 15, 260, y_base + 115], fill=LILAC_FRONT)
    draw.ellipse([210, y_base - 8, 410, y_base + 115], fill=LILAC_FRONT)
    draw.ellipse([350, y_base + 5, 520, y_base + 120], fill=LILAC_FRONT)

def draw_pixel_star(draw: ImageDraw.ImageDraw, cx: int, cy: int, size: int, color: tuple):
    """Draws a 4-pointed retro diamond cross."""
    draw.polygon([(cx, cy - size), (cx + size // 3, cy), (cx, cy + size), (cx - size // 3, cy)], fill=color)
    draw.polygon([(cx - size, cy), (cx, cy + size // 3), (cx + size, cy), (cx, cy - size // 3)], fill=color)

def draw_pixel_z(draw: ImageDraw.ImageDraw, x: int, y: int, size: int, color: tuple):
    """Draws a chunky pixel-art Z with a 2px stroke."""
    # Top horizontal bar (2px thick)
    draw.rectangle([x, y, x + size, y + 1], fill=color)
    # Stepped diagonal stroke (2px thick blocks)
    for i in range(size):
        draw.rectangle([x + size - i - 1, y + i, x + size - i, y + i + 1], fill=color)
    # Bottom horizontal bar (2px thick)
    draw.rectangle([x, y + size - 1, x + size, y + size], fill=color)

# ============================================================================
# PART 0: INTRO (Clouds rise up from bottom; Mochibunny settles into bed)
# ============================================================================
print("Rendering part0 (Intro)...")
part0_frames = 20

# SET THE HEIGHT OF BUN
mochibun_height = 132

for f in range(part0_frames):
    frame = Image.new("RGBA", (WIDTH, HEIGHT), BG_COLOR)
    draw = ImageDraw.Draw(frame)

    t = f / float(part0_frames - 1)
    cloud_y = int(HEIGHT - (t * 360))  # Rises to 440px

    # 1. Back cloud layer
    draw_back_clouds(draw, cloud_y)

    # Logo in the sky with a delayed fade-in
    if logo_img:
        # Delay the start of the fade until frame 6 (out of 20)
        delay_frames = 6
        if f < delay_frames:
            fade_opacity = 0.0
        else:
            fade_opacity = (f - delay_frames) / float(part0_frames - 1 - delay_frames)

        faded_logo = apply_alpha_fade(logo_img, fade_opacity)
        frame.alpha_composite(faded_logo, (logo_bx, LOGO_Y))

    # 2. Mochibunny resting on the back shelf
    sprite = sleep_frames[(f // 2) % len(sleep_frames)]
    bx = (WIDTH - sprite.width) // 2
    # Sits right at the cloud horizon, allowing the front layer to wrap her
    by = cloud_y - sprite.height + mochibun_height  # mochibun height 1/2 (intro)
    frame.alpha_composite(sprite, (bx, by))

    # 3. Front cloud layer (tucks her bottom edge in)
    draw_front_clouds(draw, cloud_y + 18)

    frame.convert("RGB").save(os.path.join(PART0_DIR, f"{f:03d}.png"))

# ============================================================================
# PART 1: INFINITE LOOP (Mochibun snoozing, floating Z's, twinkling stars, dots)
# ============================================================================
print("Rendering part1 (Loop)...")
loop_frames = len(sleep_frames) * 2
cloud_rest_y = 440

for f in range(loop_frames):
    frame = Image.new("RGBA", (WIDTH, HEIGHT), BG_COLOR)
    draw = ImageDraw.Draw(frame)

    # Floating logo bob (drifts smoothly up/down ~3px across the loop cycle)
    if logo_img:
        logo_bob = int(round(math.sin((f / float(loop_frames)) * 2 * math.pi) * 3))
        frame.alpha_composite(logo_img, (logo_bx, LOGO_Y + logo_bob))

    # 1. Twinkling ambient stars in the sky
    star_twinkle_1 = 6 if (f % 4 < 2) else 4
    star_twinkle_2 = 4 if (f % 4 < 2) else 6
    draw_pixel_star(draw, 80, 220, star_twinkle_1, DOT_ACTIVE)
    draw_pixel_star(draw, 400, 260, star_twinkle_2, (200, 180, 220))

    # 2. Back cloud layer
    draw_back_clouds(draw, cloud_rest_y)

    # 3. Snoozing Mochibunny
    sprite = sleep_frames[f % len(sleep_frames)]
    bx = (WIDTH - sprite.width) // 2
    by = cloud_rest_y - sprite.height + mochibun_height  # mochibun height 2/2 (loop)
    frame.alpha_composite(sprite, (bx, by))

    # 4. Front cloud layer (tucks her in like a blanket)
    draw_front_clouds(draw, cloud_rest_y + 18)

    # 5. Floating drifting Z's (drawn in foreground)
    z_phase = (f / float(loop_frames))
    z1_x = int(bx + sprite.width - 24 + (z_phase * 18) - 125)
    z1_y = int(by - 8 - (z_phase * 26) + 175)
    draw_pixel_z(draw, z1_x, z1_y, 11, TEXT_PLUM)

    if f >= loop_frames // 2:
        z2_phase = ((f - loop_frames // 2) / float(loop_frames // 2))
        z2_x = int(bx + sprite.width - 12 + (z2_phase * 12) - 125)
        z2_y = int(by - 20 - (z2_phase * 20) + 150)
        draw_pixel_z(draw, z2_x, z2_y, 8, (160, 130, 175))

    # 6. Bouncing mochi loading dots
    dot_phase = (f // 3) % 3
    dot_y = 660
    for i, dx in enumerate([WIDTH // 2 - 32, WIDTH // 2, WIDTH // 2 + 32]):
        is_active = (i == dot_phase)
        color = DOT_ACTIVE if is_active else DOT_INACTIVE
        r = 6 if is_active else 4
        y_offset = -4 if is_active else 0
        draw.ellipse([dx - r, dot_y + y_offset - r, dx + r, dot_y + y_offset + r], fill=color)

    frame.convert("RGB").save(os.path.join(PART1_DIR, f"{f:03d}.png"))

# ============================================================================
# DESC.TXT & PACKAGING (Inside output/)
# ============================================================================
desc_path = os.path.join(OUTPUT_DIR, "desc.txt")
with open(desc_path, "w", newline="\n") as f:
    f.write(f"{WIDTH} {HEIGHT} {FPS}\n")
    f.write("p 1 0 part0\n")
    f.write("p 0 0 part1\n")

zip_output_path = os.path.join(OUTPUT_DIR, "bootanimation.zip")
print(f"Packaging {zip_output_path} (STORE mode)...")
if os.path.exists(zip_output_path):
    os.remove(zip_output_path)

with zipfile.ZipFile(zip_output_path, "w", compression=zipfile.ZIP_STORED) as zipf:
    zipf.write(desc_path, arcname="desc.txt")
    for folder_name, folder_path in [("part0", PART0_DIR), ("part1", PART1_DIR)]:
        for file in sorted(os.listdir(folder_path)):
            if file.endswith(".png"):
                full_path = os.path.join(folder_path, file)
                arcname = f"{folder_name}/{file}"
                zipf.write(full_path, arcname=arcname)

# ============================================================================
# PREVIEW GIF GENERATOR (Stays in script root)
# ============================================================================
print("Rendering preview.gif...")
preview_frames = []
for file in sorted(os.listdir(PART0_DIR)):
    if file.endswith(".png"):
        preview_frames.append(Image.open(os.path.join(PART0_DIR, file)).convert("RGB"))
for _ in range(3):
    for file in sorted(os.listdir(PART1_DIR)):
        if file.endswith(".png"):
            preview_frames.append(Image.open(os.path.join(PART1_DIR, file)).convert("RGB"))

if preview_frames:
    preview_frames[0].save(
        "preview.gif",
        save_all=True,
        append_images=preview_frames[1:],
        duration=int(1000 / FPS),
        loop=0
    )
    print("♡ Done! Open preview.gif to inspect the cloud-tucked Mochibun! ♡")
