"""Create only synthetic, clearly named media for USB device smoke tests.

Requires Pillow and ffmpeg. Never reads the user's media library.
"""
from pathlib import Path
import json
import math
import struct
import subprocess
import wave

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / ".work" / "fixtures"
ROOT.mkdir(parents=True, exist_ok=True)

for name, color, shape in [("fixture-01.png", "red", "circle"), ("fixture-02.png", "blue", "square")]:
    image = Image.new("RGB", (640, 480), "white")
    draw = ImageDraw.Draw(image)
    if shape == "circle":
        draw.ellipse((170, 90, 470, 390), fill=color)
    else:
        draw.rectangle((170, 90, 470, 390), fill=color)
    image.save(ROOT / name)

(ROOT / "fixture-note.txt").write_text(
    "Keeps synthetic test note\n"
    "The bicycle repair appointment is on Saturday at 10 AM.\n"
    "Bring the green helmet and the punctured rear tire to the workshop.\n"
    "The test-only reference code is KEEPS-ORCHID-731.\n", encoding="utf-8")

with wave.open(str(ROOT / "fixture-tone.wav"), "wb") as audio:
    audio.setparams((1, 2, 16000, 0, "NONE", "not compressed"))
    audio.writeframes(b"".join(struct.pack("<h", int(9000 * math.sin(2 * math.pi * 440 * i / 16000)))
                             for i in range(16000 * 3)))

# Two contrasting pages let the preview test verify page navigation.
pages = []
for color, word in [("red", "RED CIRCLE - TEST PAGE ONE"), ("blue", "BLUE SQUARE - TEST PAGE TWO")]:
    page = Image.new("RGB", (595, 842), "white")
    draw = ImageDraw.Draw(page)
    draw.text((40, 40), word, fill="black", font_size=24)
    if color == "red":
        draw.ellipse((150, 200, 445, 495), fill=color)
    else:
        draw.rectangle((150, 200, 445, 495), fill=color)
    pages.append(page)
pages[0].save(ROOT / "fixture-pages.pdf", save_all=True, append_images=pages[1:])

subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-loop", "1", "-i", str(ROOT / "fixture-01.png"),
                "-t", "4", "-r", "24", "-c:v", "libx264", "-pix_fmt", "yuv420p",
                str(ROOT / "fixture-clip.mp4")], check=True)
(ROOT / "expected.json").write_text(json.dumps({
    "fixture-01.png": "red circle on white background",
    "fixture-02.png": "blue square on white background",
    "fixture-note.txt": "bicycle repair appointment; exact code KEEPS-ORCHID-731",
    "fixture-tone.wav": "3 second synthetic 440 Hz tone, mono PCM16 at 16 kHz",
    "fixture-pages.pdf": "2 pages, red circle then blue square",
    "fixture-clip.mp4": "4 second still red circle; proves frame search, not motion recognition",
}, indent=2), encoding="utf-8")
print(ROOT)
