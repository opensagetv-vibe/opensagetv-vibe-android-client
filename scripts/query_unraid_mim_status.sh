#!/usr/bin/env bash
set -euo pipefail

# Return the tested server's machine-readable FFmpeg/MIM status. Keeping the
# SSH invocation in one executable avoids nested-shell quoting failures in the
# commissioning matrix's --mim-status-command argument.
unraid_host="${SAGETV_UNRAID_HOST:?set SAGETV_UNRAID_HOST to the commissioning host}"
server_container="${SAGETV_TEST_CONTAINER:-sagetv-vibe-server-u26-gpu-j11}"
identity_file="${SAGETV_UNRAID_IDENTITY:-/tmp/opensagetv-vibe-unraid-key}"

exec ssh \
  -i "$identity_file" \
  -o BatchMode=yes \
  -o StrictHostKeyChecking=no \
  "root@$unraid_host" \
  docker exec "$server_container" /opt/sagetv/server/ffmpeg --mim-status
