#!/usr/bin/env bash
set -euo pipefail

workspace="${SAGETV_WORKSPACE:-/workspace/android-client}"
export SAGETV_MCP_CONFIG="${SAGETV_MCP_CONFIG:-$workspace/config/firetv.toml}"

explicit_target=false
for argument in "$@"; do
  case "$argument" in
    -s|--serial|-d|-e)
      explicit_target=true
      break
      ;;
    --)
      break
      ;;
    -*)
      ;;
    *)
      break
      ;;
  esac
done

if [[ "$explicit_target" == true ]]; then
  exec adb "$@"
fi

serial="$(python3 "$workspace/scripts/test_environment_config.py" --get device.serial)"
if [[ -z "$serial" ]]; then
  echo "ERROR: raw ADB requires an explicit target or a configured active device" >&2
  exit 2
fi

exec adb -s "$serial" "$@"
