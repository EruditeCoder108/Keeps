"""Generate a tiny three-scene H.264/AAC video for native window/resume tests."""
from pathlib import Path
import subprocess
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
frames = root / '.work/video-moments/frames'
frames.mkdir(parents=True, exist_ok=True)
for index in range(72):
    image = Image.new('RGB', (320, 240), 'white')
    draw = ImageDraw.Draw(image)
    if index < 24:
        x = 60 + index * 7
        draw.ellipse((x - 35, 85, x + 35, 155), fill='red')
    elif index < 48:
        draw.rectangle((100, 60, 220, 180), fill='blue')
    else:
        draw.polygon([(160, 45), (65, 195), (255, 195)], fill='green')
    image.save(frames / f'frame-{index:03d}.png')
output = root / 'app/src/androidTest/assets/video/fixture-moments.mp4'
output.parent.mkdir(parents=True, exist_ok=True)
subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-framerate', '4', '-i', str(frames / 'frame-%03d.png'),
                '-f', 'lavfi', '-i', 'sine=frequency=880:sample_rate=48000:duration=18',
                '-c:v', 'libx264', '-preset', 'fast', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-ac', '2',
                '-t', '18', '-movflags', '+faststart', str(output)], check=True)
print(f'Generated 18-second three-scene stereo 48 kHz AAC fixture: {output.stat().st_size} bytes')
