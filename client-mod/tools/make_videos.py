"""Turns the frames VideoTest saved into one MP4 per scene (needs ffmpeg on the PATH).

Usage (from client-mod/):
    GPTGOD_VIDEO=all ./gradlew runClientGameTest
    python tools/make_videos.py [screenshots dir] [output dir]
Defaults: build/run/clientGameTest/screenshots → ../previews/client-mod/video
"""
import glob
import os
import re
import shutil
import subprocess
import sys
import tempfile

shots = sys.argv[1] if len(sys.argv) > 1 else 'build/run/clientGameTest/screenshots'
out = sys.argv[2] if len(sys.argv) > 2 else '../previews/client-mod/video'
os.makedirs(out, exist_ok=True)
scenes = {}
for path in sorted(glob.glob(os.path.join(shots, '*video-*.png'))):
    m = re.search(r'video-(.+)-(\d{4})\.png$', os.path.basename(path))
    if m:
        scenes.setdefault(m.group(1), []).append((int(m.group(2)), path))
if not scenes:
    sys.exit('no video frames in ' + shots)
for name, frames in scenes.items():
    frames.sort()
    with tempfile.TemporaryDirectory() as tmp:
        # Frames are named by game tick; hold each one until the next so the video runs at true speed.
        last = frames[-1][0]
        at = 0
        for tick in range(last + 1):
            while at + 1 < len(frames) and frames[at + 1][0] <= tick:
                at += 1
            shutil.copy(frames[at][1], os.path.join(tmp, '%04d.png' % tick))
        target = os.path.join(out, 'scene-%s.mp4' % name)
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', '20', '-i', os.path.join(tmp, '%04d.png'),
                        '-vf', 'pad=ceil(iw/2)*2:ceil(ih/2)*2', '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '20',
                        '-movflags', '+faststart', target], check=True)
        print(target, len(frames), 'frames')
