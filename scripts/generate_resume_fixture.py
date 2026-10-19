#!/usr/bin/env python3
"""Create a copyright-free timed AVC/AAC control for initial bookmark tests."""
import argparse
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ffmpeg', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--seconds', type=int, default=240)
    args = parser.parse_args()
    if not 60 <= args.seconds <= 900:
        parser.error('seconds must be60..900')
    output = args.output.resolve()
    if output.exists():
        parser.error('refuse to overwrite an existing fixture')
    output.parent.mkdir(parents=True, exist_ok=True)
    label = ('drawtext=fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:'
             "text='RESUME-001   %{pts\\:hms}':fontcolor=white:fontsize=32:"
             'box=1:boxcolor=black@0.8:x=20:y=20')
    subprocess.run([str(args.ffmpeg.resolve()), '-hide_banner', '-loglevel', 'error',
                    '-nostdin', '-n', '-f', 'lavfi', '-i', 'testsrc2=size=640x360:rate=30',
                    '-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=48000',
                    '-t', str(args.seconds), '-vf', label, '-c:v', 'libx264',
                    '-preset', 'ultrafast', '-crf', '27', '-pix_fmt', 'yuv420p',
                    '-g', '60', '-c:a', 'aac', '-b:a', '96k', '-ac', '2',
                    '-movflags', '+faststart', str(output)], check=True, timeout=180)


if __name__ == '__main__':
    main()
