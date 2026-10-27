"""Create Keeps' 1280x640 GitHub social card from its vector identity.

No personal media, network inputs or image generation are used. Edit COPY to
reuse the template. The SVG contains outlined Geist typography so it renders
without installed fonts; regenerate to edit the copy. Requires fontTools,
svglib, reportlab, PyMuPDF and Pillow. Geist's OFL is in app/src/main/assets/notices.
"""
from pathlib import Path
from html import escape
import io
import xml.etree.ElementTree as ET
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
from reportlab.graphics import renderPDF
from svglib.svglib import svg2rlg
import pymupdf as fitz
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/brand"
FONT = ROOT / "app/src/main/res/font/geist.ttf"
COPY = {
    "name": "Keeps",
    "headline": ["Find what", "you remember."],
    "description": "Your files. Your gallery. Private, on-device search.",
    "features": "FILES  /  GALLERY  /  LOCAL SEARCH",
    "repository": "github.com/EruditeCoder108/Keeps",
    "platform": "ANDROID  /  APACHE 2.0",
}
BLACK, WHITE, RED = "#080808", "#FFFFFF", "#FF001F"
fonts = {}


def letters(value, x, baseline, size, weight=500, color=WHITE):
    if weight not in fonts:
        fonts[weight] = instantiateVariableFont(TTFont(FONT), {"wght": weight}, inplace=True)
    face = fonts[weight]
    glyphs, cmap = face.getGlyphSet(), face.getBestCmap()
    scale, cursor = size / face["head"].unitsPerEm, 0
    paths = []
    for char in value:
        glyph = glyphs[cmap[ord(char)]]
        pen = SVGPathPen(glyphs)
        glyph.draw(pen)
        paths.append(f'<path transform="translate({cursor},0)" d="{pen.getCommands()}"/>')
        cursor += glyph.width
    assert x >= 64 and x + cursor * scale <= 1216, f"Text exceeds safe area: {value}"
    assert baseline <= 576 and baseline - size >= 64, f"Text exceeds vertical safe area: {value}"
    return f'<g aria-label="{escape(value)}" fill="{color}" transform="translate({x},{baseline}) scale({scale},-{scale})">{"".join(paths)}</g>'


parts = [
    '<svg xmlns="http://www.w3.org/2000/svg" width="1280" height="640" viewBox="0 0 1280 640" role="img" aria-labelledby="title desc">',
    '<title id="title">Keeps — find what you remember</title>',
    '<desc id="desc">Android file manager, gallery and private on-device content search. Black, white and red social preview.</desc>',
    f'<path fill="{BLACK}" d="M0 0H1280V640H0Z"/>',
    f'<path fill="{RED}" d="M0 0H12V640H0Z"/>',
    '<path stroke="#333333" d="M80 158H1200M80 518H1200"/>',
    letters(COPY["name"], 80, 122, 48, 650),
    letters(COPY["features"], 655, 116, 18, 500, "#BBBBBB"),
    letters(COPY["headline"][0], 80, 279, 85, 650),
    letters(COPY["headline"][1], 80, 375, 85, 650),
    letters(COPY["description"], 83, 443, 23, 400, "#BBBBBB"),
    letters(COPY["repository"], 80, 570, 18, 450, "#BBBBBB"),
    letters(COPY["platform"], 929, 570, 17, 500, "#BBBBBB"),
    f'<rect x="918" y="199" width="282" height="270" rx="24" fill="{WHITE}"/>',
]
# Reuse the exact four-tile identity. Crop its vector viewport, not a photo.
logo = ET.parse(OUT / "keeps-mark.svg").getroot()
paths = [node for node in logo.iter() if node.tag.endswith("}path")]
parts.append('<g transform="translate(948,240) scale(3.58) translate(-23,-25)">')
for node in paths:
    parts.append(f'<path fill="{node.attrib["fill"]}" d="{node.attrib["d"]}"/>')
parts.extend(['</g>', f'<circle cx="1188" cy="82" r="7" fill="{RED}"/>', '</svg>'])
source = "\n".join(parts) + "\n"
svg_path = OUT / "keeps-repo-card.svg"
png_path = OUT / "keeps-repo-card.png"
svg_path.write_text(source, encoding="utf-8")
drawing = svg2rlg(io.BytesIO(source.encode()))
with fitz.open(stream=renderPDF.drawToString(drawing), filetype="pdf") as pdf:
    page = pdf[0]
    pix = page.get_pixmap(matrix=fitz.Matrix(1280 / page.rect.width, 640 / page.rect.height), alpha=False)
    image = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
    image.save(png_path, optimize=True)
assert image.size == (1280, 640)
assert png_path.stat().st_size < 1_000_000
print(f"Created {png_path.relative_to(ROOT)} ({png_path.stat().st_size:,} bytes), plus self-contained SVG template.")
