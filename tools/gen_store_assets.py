#!/usr/bin/env python3
"""Builds Play Store graphics from raw 1080x2400 emulator screenshots in store/raw (needs Pillow)."""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), "..", "store")
RAW, SHOTS = os.path.join(ROOT, "raw"), os.path.join(ROOT, "screenshots")
os.makedirs(SHOTS, exist_ok=True)
FONT = "/System/Library/Fonts/Helvetica.ttc"
ACCENT = "#2F5BEA"


def crop(name, out, top, height):
    # Play wants the long side <= 2x the short side: drop the status bar, and on game screens everything from the
    # banner ad down (store images must not show ads).
    im = Image.open(os.path.join(RAW, name + ".png")).convert("RGB")
    im.crop((0, top, im.width, top + height)).save(os.path.join(SHOTS, out + ".png"), optimize=True)


crop("1_home", "1-home", 100, 2060)
crop("2_heart", "2-heart", 100, 2000)
crop("3_playful", "3-playful", 100, 2000)
crop("4_dense", "4-dense", 100, 2000)

# The 512x512 icon comes from tools/gen_icon.py (same geometry as the launcher icon).

# --- 1024x500 feature graphic: accent gradient, title left, the colourful heart board right
W, H = 1024, 500
fg = Image.new("RGB", (W, H))
g = ImageDraw.Draw(fg)
for x in range(W):  # left-to-right gradient from the accent blue to a deeper indigo
    t = x / W
    g.line((x, 0, x, H), fill=(int(47 - 20 * t), int(91 - 45 * t), int(234 - 60 * t)))
board = Image.open(os.path.join(RAW, "3_playful.png")).convert("RGB").crop((40, 430, 1040, 1690))
board = board.resize((int(board.width * 440 / board.height), 440), Image.LANCZOS)
card = Image.new("RGB", (board.width + 40, board.height + 40), "white")
card.paste(board, (20, 20))
mask = Image.new("L", card.size, 0)
ImageDraw.Draw(mask).rounded_rectangle((0, 0, *card.size), radius=28, fill=255)
fg.paste(card, (W - card.width - 40, (H - card.height) // 2), mask)
g = ImageDraw.Draw(fg)
g.text((56, 120), "Arrow", font=ImageFont.truetype(FONT, 104, index=1), fill="white")
g.text((56, 228), "Escape", font=ImageFont.truetype(FONT, 104, index=1), fill="white")
g.text((60, 360), "Tap the arrows out, in the right order.", font=ImageFont.truetype(FONT, 26), fill="#DCE4FF")
fg.save(os.path.join(ROOT, "feature-graphic.png"), optimize=True)
print("ok")
