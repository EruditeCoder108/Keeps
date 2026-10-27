"""Generated-only fixtures for ninth-window continuation and compressed audio."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
assets = root / "app/src/androidTest/assets"
source = assets / "video/fixture-moments.mp4"
video = assets / "video/fixture-long-worker.mp4"
audio = assets / "audio"
audio.mkdir(parents=True, exist_ok=True)
subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-stream_loop", "2", "-i", str(source),
                "-t", "54", "-c", "copy", "-movflags", "+faststart", str(video)], check=True)
# Distinct tone/noise/silence chapters; synthetic stereo 44.1 kHz, never speech.
short = audio / "fixture-audio-scenes.m4a"
subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
    "-f", "lavfi", "-i", "sine=frequency=880:sample_rate=44100:duration=6",
    "-f", "lavfi", "-i", "anoisesrc=color=white:seed=731:sample_rate=44100:duration=6",
    "-f", "lavfi", "-i", "anullsrc=r=44100:cl=mono:d=6",
    "-filter_complex", "[0:a][1:a][2:a]concat=n=3:v=0:a=1,pan=stereo|c0=c0|c1=c0[out]",
    "-map", "[out]", "-c:a", "aac", "-b:a", "96k", "-movflags", "+faststart", str(short)], check=True)
subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(short),
    "-c:a", "libmp3lame", "-b:a", "96k", str(audio / "fixture-audio-scenes.mp3")], check=True)
subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-stream_loop", "2", "-i", str(short),
    "-t", "54", "-c", "copy", "-movflags", "+faststart", str(audio / "fixture-audio-long.m4a")], check=True)
for file in (video, *audio.glob("fixture-*")):
    print(file.name, file.stat().st_size)
