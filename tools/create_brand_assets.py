"""Export Keeps' original vector mark and Android launcher resources.

SVG is the editable master. PNG exports are rasterized from that vector via PDF,
not hand-redrawn. Requires the workspace's svglib, reportlab and PyMuPDF.
"""
from pathlib import Path
import io
import pymupdf as fitz
from PIL import Image, ImageDraw
from reportlab.graphics import renderPDF
from svglib.svglib import svg2rlg

ROOT = Path(__file__).resolve().parents[1]
BRAND = ROOT / "docs/brand"
RES = ROOT / "app/src/main/res"
BRAND.mkdir(parents=True, exist_ok=True)

RED = "#FF001F"
CREAM = "#FFFFFF"
# User-selected geometric file/gallery tile mark. Native paths keep launcher
# edges exact at every density; the supplied raster and AI cutout are retained.
PATHS = [
    ("#000000", "M28 33 H46 Q51 33 51 38 V48 Q51 53 46 53 H28 Q23 53 23 48 V38 Q23 33 28 33 Z"),
    (RED, "M59 28 L77 25 Q84 24 84 31 V48 Q84 53 79 53 H59 Q54 53 54 48 V35 Q54 29 59 28 Z"),
    ("#000000", "M28 56 H39 Q44 56 44 61 V72 Q44 77 39 77 H28 Q23 77 23 72 V61 Q23 56 28 56 Z"),
    ("#000000", "M51 56 H79 Q84 56 84 61 V72 Q84 77 79 77 H51 Q46 77 46 72 V61 Q46 56 51 56 Z"),
]

def svg(background=True, monochrome=False):
    back = '<path fill="#FFFFFF" d="M0 0 H108 V108 H0 Z"/>' if background else ""
    mark = "".join(f'<path fill-rule="evenodd" fill="{CREAM if monochrome else color}" d="{path}"/>' for color, path in PATHS)
    mark = '<g transform="translate(54 54) scale(0.76) translate(-54 -54)">' + mark + '</g>'
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108"><title>Keeps — four tiles</title>{back}{mark}</svg>\n'

def vector(paths, background=False):
    back = '    <path android:fillColor="#FFFFFF" android:pathData="M0,0H108V108H0Z" />\n' if background else ""
    mark = "".join(f'    <path android:fillType="evenOdd" android:fillColor="{color}" android:pathData="{path}" />\n' for color, path in paths)
    mark = '    <group android:pivotX="54" android:pivotY="54" android:scaleX="0.76" android:scaleY="0.76">\n' + mark + '    </group>\n'
    return ('<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
            + back + mark + '</vector>\n')

def raster(source, size=512):
    drawing = svg2rlg(io.BytesIO(source.encode()))
    pdf = fitz.open(stream=renderPDF.drawToString(drawing), filetype="pdf")
    page = pdf[0]
    pix = page.get_pixmap(matrix=fitz.Matrix(size / page.rect.width, size / page.rect.height), alpha=True)
    result = Image.frombytes("RGBA", [pix.width, pix.height], pix.samples)
    pdf.close()
    return result

for name, source in (("keeps-icon", svg()), ("keeps-mark", svg(False)), ("keeps-monochrome", svg(False, True))):
    (BRAND / f"{name}.svg").write_text(source, encoding="utf-8")
    raster(source, 2048).resize((512, 512), Image.Resampling.LANCZOS).save(BRAND / f"{name}.png")

(RES / "drawable/ic_keeps.xml").write_text(vector(PATHS, True), encoding="utf-8")
(RES / "drawable/ic_keeps_foreground.xml").write_text(vector(PATHS), encoding="utf-8")
(RES / "drawable/ic_keeps_monochrome.xml").write_text(vector([("#FFFFFF", p) for _, p in PATHS]), encoding="utf-8")
(RES / "values/brand.xml").write_text('<resources>\n    <color name="keeps_icon_background">#FFFFFF</color>\n</resources>\n', encoding="utf-8")
for version in (26, 33):
    folder = RES / f"mipmap-anydpi-v{version}"
    folder.mkdir(exist_ok=True)
    mono = '    <monochrome android:drawable="@drawable/ic_keeps_monochrome" />\n' if version == 33 else ""
    (folder / "ic_launcher.xml").write_text('<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/keeps_icon_background" />\n'
        '    <foreground android:drawable="@drawable/ic_keeps_foreground" />\n' + mono + '</adaptive-icon>\n', encoding="utf-8")

# Review actual mark under common launcher masks and at small sizes.
sheet = Image.new("RGB", (1024, 640), "#EEEEEE")
draw = ImageDraw.Draw(sheet)
full = raster(svg(), 1024)
# Android adaptive foreground is 108 dp with a central 72 dp launcher viewport.
# Preview that actual crop, rather than an incorrectly unscaled 108 dp mark.
icon = full.crop((171, 171, 853, 853)).resize((1024, 1024), Image.Resampling.LANCZOS)
for label, x, radius in (("Circle", 64, 160), ("Rounded square", 352, 38), ("Themed", 640, 160)):
    tile = icon.resize((256, 256), Image.Resampling.LANCZOS)
    if label == "Themed":
        tile = Image.new("RGBA", (256, 256), "#DBD3C8")
        mono = raster(svg(False, True), 1024).crop((171, 171, 853, 853)).resize((256, 256), Image.Resampling.LANCZOS)
        tile.paste(Image.new("RGBA", tile.size, "#48342F"), mask=mono.getchannel("A"))
    mask = Image.new("L", (256, 256))
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 255, 255), radius=radius, fill=255)
    sheet.paste(tile, (x, 72), mask)
    draw.text((x, 350), label, fill="#48342F")
for i, size in enumerate((96, 64, 48, 32)):
    tile = icon.resize((size, size), Image.Resampling.LANCZOS)
    mask = Image.new("L", (size, size))
    ImageDraw.Draw(mask).ellipse((0, 0, size-1, size-1), fill=255)
    x = 80 + i * 200
    sheet.paste(tile, (x, 450), mask)
    draw.text((x, 560), f"{size}px", fill="#48342F")
sheet.save(BRAND / "keeps-icon-preview.png")
print(BRAND / "keeps-icon-preview.png")
