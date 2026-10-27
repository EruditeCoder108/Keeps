"""Reproducible Keeps README artwork. No personal media or network input.

Requires Pillow, svglib, reportlab and PyMuPDF (as does create_brand_assets.py).
Uses the existing vector logo and bundled OFL Geist font. The animation is a
diagram using geometric fixtures, not a recording or an inference benchmark.
"""
from pathlib import Path
import io
import math
from PIL import Image, ImageDraw, ImageFont
from reportlab.graphics import renderPDF
from svglib.svglib import svg2rlg
import pymupdf as fitz

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/readme"
OUT.mkdir(parents=True, exist_ok=True)
BLACK, RED, WHITE, GREY = "#080808", "#FF001F", "#FFFFFF", "#737373"
FONT = ROOT / "app/src/main/res/font/geist.ttf"


def font(size, weight=400):
    result = ImageFont.truetype(str(FONT), size)
    result.set_variation_by_axes([weight])
    return result


def text(draw, xy, value, size=24, color=BLACK, weight=400):
    draw.text(xy, value, font=font(size, weight), fill=color)


def mark(size):
    drawing = svg2rlg(str(ROOT / "docs/brand/keeps-mark.svg"))
    with fitz.open(stream=renderPDF.drawToString(drawing), filetype="pdf") as pdf:
        page = pdf[0]
        pix = page.get_pixmap(matrix=fitz.Matrix(size / page.rect.width, size / page.rect.height), alpha=True)
        return Image.frombytes("RGBA", (pix.width, pix.height), pix.samples)


def dots(draw, x, y, cols, rows, gap=15, color="#E4E4E4"):
    for row in range(rows):
        for col in range(cols):
            draw.ellipse((x + col * gap, y + row * gap, x + col * gap + 3, y + row * gap + 3), fill=color)


hero = Image.new("RGB", (1600, 850), WHITE)
d = ImageDraw.Draw(hero)
d.rounded_rectangle((1, 1, 1598, 848), radius=32, outline="#E5E5E5", width=2)
hero.paste(mark(102), (35, 15), mark(102))
text(d, (137, 39), "Keeps", 43, weight=650)
text(d, (1198, 57), "ANDROID / LOCAL FIRST", 20, color=GREY, weight=550)
d.line((64, 133, 1536, 133), fill="#E7E7E7", width=2)
text(d, (64, 181), "Everything", 118, weight=650)
text(d, (64, 307), "worth keeping.", 118, weight=650)
text(d, (68, 465), "Your files. Your gallery. Search that stays on your phone.", 27, color=GREY)
d.rounded_rectangle((64, 554, 894, 648), radius=18, fill=BLACK)
d.ellipse((95, 582, 117, 604), outline=WHITE, width=3)
d.line((114, 601, 126, 613), fill=WHITE, width=3)
text(d, (149, 581), "Find what you remember.", 30, WHITE, 500)
d.rounded_rectangle((817, 574, 874, 629), radius=12, fill=RED)
d.line((833, 601, 857, 601), fill=WHITE, width=3)
d.line((850, 593, 858, 601, 850, 609), fill=WHITE, width=3)
dots(d, 1007, 200, 29, 26, 17)
hero.paste(mark(600), (952, 172), mark(600))
text(d, (1092, 640), "A LITTLE ORDER.", 19, color=GREY, weight=550)
text(d, (1092, 670), "A LOT TO FIND.", 19, color=BLACK, weight=650)
d.line((64, 729, 1536, 729), fill="#E7E7E7", width=2)
for x, number, title in [(64, "01", "BROWSE"), (543, "02", "GALLERY"), (1050, "03", "CONTENT SEARCH")]:
    text(d, (x, 771), number, 19, RED, 650)
    text(d, (x + 52, 765), title, 25, BLACK, 550)
hero.save(OUT / "hero.png", optimize=True)


def scene(t):
    image = Image.new("RGB", (1200, 390), BLACK)
    draw = ImageDraw.Draw(image)
    text(draw, (42, 27), "FROM A DESCRIPTION TO A MOMENT", 19, WHITE, 550)
    text(draw, (875, 30), "ILLUSTRATIVE WORKFLOW", 15, "#929292", 500)
    draw.rounded_rectangle((42, 82, 530, 147), radius=13, fill="#1D1D1D", outline="#393939")
    draw.ellipse((65, 103, 82, 120), outline=WHITE, width=2)
    draw.line((80, 118, 88, 126), fill=WHITE, width=2)
    text(draw, (105, 100), "a red circle in the video", 24, WHITE, 450)
    draw.line((552, 115, 630, 115), fill="#494949", width=2)
    draw.line((621, 108, 630, 115, 621, 122), fill="#494949", width=2)
    for i in range(4):
        x = 658 + i * 125
        highlight = i == 2 and t >= 0.32
        draw.rounded_rectangle((x, 82, x + 106, 158), radius=9, fill="#F2F2F2", outline=RED if highlight else "#484848", width=3)
        if i in (1, 2):
            draw.ellipse((x + 32, 99, x + 72, 139), fill=RED if i == 2 else "#B3B3B3")
        else:
            draw.rectangle((x + 32, 99, x + 72, 139), fill="#B3B3B3")
    text(draw, (42, 186), "Describe it.", 35, WHITE, 600)
    text(draw, (658, 186), "Open the matching interval.", 29, WHITE, 550)
    start, end = 658, 1139
    draw.rounded_rectangle((start, 260, end, 266), radius=3, fill="#383838")
    draw.rounded_rectangle((start + 244, 256, start + 354, 270), radius=5, fill=RED)
    # Slow reveal and settle; no flashing, simulated latency or real file data.
    progress = max(0, min(1, (t - 0.2) / 0.55))
    eased = (1 - math.cos(progress * math.pi)) / 2
    play = int(start + 20 + eased * 280)
    draw.ellipse((play - 6, 257, play + 6, 269), fill=WHITE)
    text(draw, (658, 294), "ORDERED FRAMES + AVAILABLE SOUNDTRACK", 15, "#9C9C9C", 500)
    text(draw, (42, 298), "Files stay on your phone.", 20, "#A9A9A9", 450)
    text(draw, (42, 345), "DIAGRAM / NOT AN APP RECORDING OR A SPEED BENCHMARK", 13, "#777777", 500)
    return image


scene(0.85).save(OUT / "search-story.png", optimize=True)
frames = [scene(i / 39).quantize(colors=64) for i in range(40)]
frames[0].save(OUT / "search-story.gif", save_all=True, append_images=frames[1:], duration=[100] * 39 + [1800], loop=0, optimize=True, disposal=2)
print("Created hero.png, search-story.png and search-story.gif from vector/code assets.")
