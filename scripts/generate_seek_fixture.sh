#!/usr/bin/env bash
set -euo pipefail

# Generate the canonical deterministic SageTV seek/caption fixture:
# MPEG-TS, 1920x1080 interlaced MPEG-2 at 30000/1001 fps, dual AC-3 audio,
# and real in-band CEA-608 CC1 plus CEA-708 Service 1 carried as ATSC A/53
# GA94 user_data. Burned-in PTS/frame text makes stale video after a seek
# visible; the caption clock updates every 0.5 seconds independently.

script_dir="$(cd "$(dirname "$0")" && pwd)"
output_path="${1:-artifacts/test-media/VibeSeekTest-1080i-MPEG2-AC3-CC.ts}"
duration_seconds="${2:-900}"

case "$duration_seconds" in
  ''|*[!0-9]*)
    echo "duration must be a positive integer number of seconds" >&2
    exit 2
    ;;
esac
if [ "$duration_seconds" -le 0 ]; then
  echo "duration must be greater than zero" >&2
  exit 2
fi

mkdir -p "$(dirname "$output_path")"
python3 "$script_dir/generate_a53_seek_fixture.py" \
  --duration "$duration_seconds" \
  --caption-interval 0.5 \
  --cc both \
  --caption-prefix PTS \
  --output "$output_path"

ffprobe -v error \
  -show_entries format=filename,duration,size,bit_rate:stream=index,codec_name,codec_type,width,height,r_frame_rate,field_order,sample_rate,channels \
  -of json "$output_path"

# Create deterministic Comskip markers beside the transport stream. Only
# complete intervals that fit inside a caller-shortened fixture are emitted.
edl_path="${output_path%.*}.edl"
: > "$edl_path"
for marker in "120 180" "360 420" "660 720"; do
  read -r marker_start marker_end <<< "$marker"
  if [ "$duration_seconds" -ge "$marker_end" ]; then
    printf '%s.000\t%s.000\t0\n' "$marker_start" "$marker_end" >> "$edl_path"
  fi
done
printf 'Comskip EDL: %s\n' "$edl_path"
