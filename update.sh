#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

DOWNLOAD_DIR="${OPENSAGETV_VIBE_DOWNLOAD_DIR:-$ROOT/artifacts/downloads}"
STATE_DIR="$ROOT/artifacts/update_runner"
STATE_FILE="$STATE_DIR/state.env"
SCRIPTED_CLIENT_ID="44:45:56:30:30:31"

mkdir -p "$DOWNLOAD_DIR" "$STATE_DIR"

STATE_VERSION=""
STATE_PACKAGE=""
STATE_PACKAGE_SHA256=""
STATE_REQUIRES_BUILD=""
STATE_UNZIP_DONE="false"
STATE_TEST_DONE="false"
STATE_VALIDATE_DONE="false"
STATE_BUILD_DONE="false"
STATE_INSTALL_DONE="false"

run_step() {
    local name="$1"
    shift

    echo
    echo "============================================================"
    echo "STEP: $name"
    echo "============================================================"

    "$@"

    echo
    echo "PASS: $name"
}

read_current_version() {
    if [[ ! -f "$ROOT/VERSION" ]]; then
        echo "ERROR: missing $ROOT/VERSION" >&2
        return 1
    fi
    tr -d '[:space:]' < "$ROOT/VERSION"
}

version_gt() {
    local left="$1"
    local right="$2"
    [[ "$left" != "$right" ]] && [[ "$(printf '%s\n%s\n' "$left" "$right" | sort -V | tail -n 1)" == "$left" ]]
}

package_version_from_name() {
    local base
    base="$(basename "$1")"
    if [[ "$base" =~ ^opensagetv-vibe-android-v([0-9]+\.[0-9]+\.[0-9]+).*changed-files-only.*\.zip$ ]]; then
        printf '%s\n' "${BASH_REMATCH[1]}"
        return 0
    fi
    return 1
}

load_state() {
    [[ -f "$STATE_FILE" ]] || return 0

    local key value
    while IFS='=' read -r key value; do
        case "$key" in
            STATE_VERSION) STATE_VERSION="$value" ;;
            STATE_PACKAGE) STATE_PACKAGE="$value" ;;
            STATE_PACKAGE_SHA256) STATE_PACKAGE_SHA256="$value" ;;
            STATE_REQUIRES_BUILD) STATE_REQUIRES_BUILD="$value" ;;
            STATE_UNZIP_DONE) STATE_UNZIP_DONE="$value" ;;
            STATE_TEST_DONE) STATE_TEST_DONE="$value" ;;
            STATE_VALIDATE_DONE) STATE_VALIDATE_DONE="$value" ;;
            STATE_BUILD_DONE) STATE_BUILD_DONE="$value" ;;
            STATE_INSTALL_DONE) STATE_INSTALL_DONE="$value" ;;
        esac
    done < "$STATE_FILE"
}

save_state() {
    local tmp="$STATE_FILE.tmp"
    cat > "$tmp" <<EOF_STATE
STATE_VERSION=$STATE_VERSION
STATE_PACKAGE=$STATE_PACKAGE
STATE_PACKAGE_SHA256=$STATE_PACKAGE_SHA256
STATE_REQUIRES_BUILD=$STATE_REQUIRES_BUILD
STATE_UNZIP_DONE=$STATE_UNZIP_DONE
STATE_TEST_DONE=$STATE_TEST_DONE
STATE_VALIDATE_DONE=$STATE_VALIDATE_DONE
STATE_BUILD_DONE=$STATE_BUILD_DONE
STATE_INSTALL_DONE=$STATE_INSTALL_DONE
EOF_STATE
    mv -f "$tmp" "$STATE_FILE"
}

read_release_metadata_from_text() {
    local text="$1"
    local expected_version="$2"
    local versions build_values version build_value

    versions="$(printf '%s\n' "$text" | tr -d '\r' | sed -n -E 's/^VERSION=([0-9]+\.[0-9]+\.[0-9]+)$/\1/p')"
    build_values="$(printf '%s\n' "$text" | tr -d '\r' | sed -n -E 's/^REQUIRES_BUILD=(true|false)$/\1/p')"
    if [[ "$(printf '%s\n' "$versions" | sed '/^$/d' | wc -l | tr -d '[:space:]')" != "1" ]] ||
       [[ "$(printf '%s\n' "$build_values" | sed '/^$/d' | wc -l | tr -d '[:space:]')" != "1" ]]; then
        return 1
    fi

    version="$(printf '%s\n' "$versions" | sed '/^$/d')"
    build_value="$(printf '%s\n' "$build_values" | sed '/^$/d')"
    [[ "$version" == "$expected_version" ]] || return 1
    case "$build_value" in
        true|false) printf '%s\n' "$build_value" ;;
        *) return 1 ;;
    esac
}

read_local_requires_build() {
    local version="$1"
    local metadata_file="$ROOT/release.properties"
    local value

    if [[ -f "$metadata_file" ]]; then
        if value="$(read_release_metadata_from_text "$(cat "$metadata_file")" "$version")"; then
            printf '%s\n' "$value"
            return 0
        fi
    fi

    # Conservative fallback for a checkout that predates stable release metadata.
    printf 'true\n'
}

reset_state_for_version() {
    local version="$1"
    local requires_build="$2"
    local package_name="${3:-}"
    local package_sha256="${4:-}"

    STATE_VERSION="$version"
    STATE_PACKAGE="$package_name"
    STATE_PACKAGE_SHA256="$package_sha256"
    STATE_REQUIRES_BUILD="$requires_build"
    STATE_UNZIP_DONE="true"
    STATE_TEST_DONE="false"
    STATE_VALIDATE_DONE="false"
    STATE_BUILD_DONE="false"
    STATE_INSTALL_DONE="false"
    save_state
}

select_newest_update() {
    local current_version="$1"
    local best_file=""
    local best_version=""
    local best_mtime="-1"
    local file version mtime

    while IFS= read -r -d '' file; do
        version="$(package_version_from_name "$file" || true)"
        [[ -n "$version" ]] || continue
        version_gt "$version" "$current_version" || continue
        mtime="$(stat -c '%Y' "$file" 2>/dev/null || printf '0')"

        if [[ -z "$best_file" ]] || version_gt "$version" "$best_version" || { [[ "$version" == "$best_version" ]] && (( mtime > best_mtime )); }; then
            best_file="$file"
            best_version="$version"
            best_mtime="$mtime"
        fi
    done < <(find "$DOWNLOAD_DIR" -maxdepth 1 -type f -name 'opensagetv-vibe-android-v*changed-files-only*.zip' -print0 2>/dev/null)

    NEWEST_UPDATE_FILE="$best_file"
    NEWEST_UPDATE_VERSION="$best_version"
}

is_safe_project_path() {
    local path="$1"
    [[ -n "$path" ]] || return 1
    [[ "$path" != /* && "$path" != \\* ]] || return 1
    [[ "$path" != *\\* && "$path" != *:* ]] || return 1
    [[ "$path" != "." && "$path" != ".." && "$path" != ./* ]] || return 1
    [[ "$path" != ../* && "$path" != */../* && "$path" != */.. ]] || return 1
}

matches_manifest_digest() {
    local file="$1"
    local expected="$2"
    local actual

    actual="$(sha256sum "$file" | awk '{print $1}')"
    [[ "$actual" == "$expected" ]] && return 0

    # A Git checkout on Windows may materialize an otherwise identical UTF-8
    # text file with CRLF endings.  The release manifest intentionally records
    # the portable LF form.  Accept only that one precisely defined text
    # transformation; binary data, invalid UTF-8, lone CR bytes, and every
    # other content change must continue to fail closed.
    python3 - "$file" "$expected" <<'PY'
import hashlib
from pathlib import Path
import sys

data = Path(sys.argv[1]).read_bytes()
try:
    data.decode("utf-8")
except UnicodeDecodeError:
    raise SystemExit(1)

if b"\r\n" not in data:
    raise SystemExit(1)
normalized = data.replace(b"\r\n", b"\n")
if b"\r" in normalized:
    raise SystemExit(1)
raise SystemExit(0 if hashlib.sha256(normalized).hexdigest() == sys.argv[2] else 1)
PY
}

verify_update_manifest() (
    set -euo pipefail

    local zip_file="$1"
    local staging manifest line digest path actual duplicate
    declare -A expected=()
    declare -A packaged=()

    duplicate="$(unzip -Z1 "$zip_file" | tr -d '\r' | sed '/\/$/d' | sort | uniq -d | head -n 1)"
    if [[ -n "$duplicate" ]]; then
        echo "ERROR: update ZIP contains duplicate entry: $duplicate" >&2
        return 1
    fi

    while IFS= read -r path; do
        path="${path%$'\r'}"
        [[ -n "$path" && "$path" != */ ]] || continue
        if ! is_safe_project_path "$path"; then
            echo "ERROR: update ZIP contains unsafe path: $path" >&2
            return 1
        fi
    done < <(unzip -Z1 "$zip_file")

    staging="$(mktemp -d "$STATE_DIR/update-preflight.XXXXXX")"
    trap 'rm -rf -- "$staging"' EXIT

    python3 - "$zip_file" <<'PY'
import stat
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as archive:
    for entry in archive.infolist():
        mode = (entry.external_attr >> 16) & 0xFFFF
        if stat.S_ISLNK(mode):
            raise SystemExit(f"ERROR: update ZIP contains symbolic link: {entry.filename}")
PY

    unzip -q "$zip_file" -d "$staging"

    manifest="$staging/PROJECT_MANIFEST.sha256"
    if [[ ! -f "$manifest" ]]; then
        echo "ERROR: update ZIP is missing the complete PROJECT_MANIFEST.sha256." >&2
        return 1
    fi

    while IFS= read -r line || [[ -n "$line" ]]; do
        line="${line%$'\r'}"
        [[ -n "$line" ]] || continue
        if [[ ! "$line" =~ ^([0-9a-fA-F]{64})\ \ (.+)$ ]]; then
            echo "ERROR: invalid PROJECT_MANIFEST.sha256 line: $line" >&2
            return 1
        fi
        digest="${BASH_REMATCH[1],,}"
        path="${BASH_REMATCH[2]}"
        if ! is_safe_project_path "$path"; then
            echo "ERROR: manifest contains unsafe path: $path" >&2
            return 1
        fi
        if [[ -n "${expected[$path]+present}" ]]; then
            echo "ERROR: manifest contains duplicate path: $path" >&2
            return 1
        fi
        expected["$path"]="$digest"
    done < "$manifest"

    if [[ -f "$staging/release-deletions.lst" ]]; then
        while IFS= read -r path || [[ -n "$path" ]]; do
            path="${path%$'\r'}"
            [[ -n "$path" && "$path" != \#* ]] || continue
            if ! is_safe_project_path "$path"; then
                echo "ERROR: release-deletions.lst contains unsafe path: $path" >&2
                return 1
            fi
            if [[ "$path" == "release-deletions.lst" || "$path" == "PROJECT_MANIFEST.sha256" ]]; then
                echo "ERROR: release-deletions.lst cannot delete update control file: $path" >&2
                return 1
            fi
            if [[ -n "${expected[$path]+present}" ]]; then
                echo "ERROR: deletion path is still present in the new full manifest: $path" >&2
                return 1
            fi
        done < "$staging/release-deletions.lst"
    fi

    while IFS= read -r path; do
        path="${path%$'\r'}"
        [[ -n "$path" && "$path" != */ && "$path" != "PROJECT_MANIFEST.sha256" ]] || continue
        if [[ -z "${expected[$path]+present}" ]]; then
            echo "ERROR: packaged file is missing from the full manifest: $path" >&2
            return 1
        fi
        actual="$(sha256sum "$staging/$path" | awk '{print $1}')"
        if [[ "$actual" != "${expected[$path]}" ]]; then
            echo "ERROR: packaged file hash does not match the full manifest: $path" >&2
            return 1
        fi
        packaged["$path"]=true
    done < <(unzip -Z1 "$zip_file")

    for path in "${!expected[@]}"; do
        [[ -z "${packaged[$path]+present}" ]] || continue
        if [[ ! -f "$ROOT/$path" ]]; then
            echo "ERROR: update baseline file is missing: $path" >&2
            return 1
        fi
        if ! matches_manifest_digest "$ROOT/$path" "${expected[$path]}"; then
            echo "ERROR: update baseline hash mismatch: $path" >&2
            return 1
        fi
    done
)

apply_release_deletions() {
    local list_file="$ROOT/release-deletions.lst"
    local path target parent resolved_parent
    [[ -f "$list_file" ]] || return 0

    while IFS= read -r path || [[ -n "$path" ]]; do
        path="${path%$'\r'}"
        [[ -n "$path" && "$path" != \#* ]] || continue
        if ! is_safe_project_path "$path"; then
            echo "ERROR: release-deletions.lst contains unsafe path: $path" >&2
            return 1
        fi
        target="$ROOT/$path"
        parent="$(dirname "$target")"
        resolved_parent="$(realpath -m -- "$parent")"
        case "$resolved_parent" in
            "$ROOT"|"$ROOT"/*) ;;
            *)
                echo "ERROR: deletion path escapes the project through a symlink: $path" >&2
                return 1
                ;;
        esac
        if [[ -d "$target" && ! -L "$target" ]]; then
            echo "ERROR: release deletion refuses directory: $path" >&2
            return 1
        fi
        if [[ -f "$target" || -L "$target" ]]; then
            rm -f -- "$target"
            echo "UPDATE: removed obsolete file $path"
        fi
    done < "$list_file"
}

normalize_manifest_text_files() {
    # Preflight has already proven every un-packaged file is either byte-exact
    # or a strict UTF-8 CRLF equivalent. Canonicalize the latter after payload
    # extraction so the resulting checkout satisfies the exact LF manifest.
    python3 - "$ROOT" <<'PY'
import hashlib
from pathlib import Path
import sys

root = Path(sys.argv[1])
manifest = root / "PROJECT_MANIFEST.sha256"
normalized_count = 0
for raw_line in manifest.read_text(encoding="ascii").splitlines():
    if not raw_line:
        continue
    expected, relative = raw_line.split("  ", 1)
    target = root / relative
    if not target.is_file():
        continue
    data = target.read_bytes()
    if hashlib.sha256(data).hexdigest() == expected:
        continue
    try:
        data.decode("utf-8")
    except UnicodeDecodeError:
        raise SystemExit(f"ERROR: manifest file changed after preflight: {relative}")
    canonical = data.replace(b"\r\n", b"\n")
    if b"\r" in canonical or hashlib.sha256(canonical).hexdigest() != expected:
        raise SystemExit(f"ERROR: manifest file changed after preflight: {relative}")
    target.write_bytes(canonical)
    normalized_count += 1
print(f"UPDATE: normalized {normalized_count} CRLF-equivalent manifest files to portable LF")
PY
}

verify_update_package() {
    local zip_file="$1"
    local expected_version="$2"
    local zip_version metadata requires_build

    unzip -tq "$zip_file" >/dev/null
    # Validate entry uniqueness, paths, links, and hashes before reading named
    # members. `unzip -p` concatenates duplicate entries and is therefore not a
    # safe metadata parser until duplicate names have been rejected.
    verify_update_manifest "$zip_file"

    zip_version="$(unzip -p "$zip_file" VERSION 2>/dev/null | tr -d '\r[:space:]')"
    if [[ "$zip_version" != "$expected_version" ]]; then
        echo "ERROR: update filename says v$expected_version but ZIP VERSION is '${zip_version:-missing}'." >&2
        return 1
    fi

    metadata="$(unzip -p "$zip_file" release.properties 2>/dev/null || true)"
    if [[ -z "$metadata" ]]; then
        echo "ERROR: update ZIP is missing release.properties metadata." >&2
        return 1
    fi

    if ! requires_build="$(read_release_metadata_from_text "$metadata" "$expected_version")"; then
        echo "ERROR: release.properties must contain exactly one VERSION=$expected_version and one REQUIRES_BUILD=true|false assignment." >&2
        return 1
    fi

    UPDATE_REQUIRES_BUILD="$requires_build"
}

apply_new_update_if_available() {
    local current_version="$1"
    local package_sha new_version_after

    select_newest_update "$current_version"
    if [[ -z "${NEWEST_UPDATE_FILE:-}" ]]; then
        echo "UPDATE: no changed-files ZIP newer than v$current_version in artifacts/downloads; skipping unzip."
        return 0
    fi

    echo "UPDATE: found newer package v$NEWEST_UPDATE_VERSION"
    echo "        $(basename "$NEWEST_UPDATE_FILE")"
    verify_update_package "$NEWEST_UPDATE_FILE" "$NEWEST_UPDATE_VERSION"
    package_sha="$(sha256sum "$NEWEST_UPDATE_FILE" | awk '{print $1}')"

    run_step "UNZIP v$NEWEST_UPDATE_VERSION" unzip -o "$NEWEST_UPDATE_FILE" -d "$ROOT"

    new_version_after="$(read_current_version)"
    if [[ "$new_version_after" != "$NEWEST_UPDATE_VERSION" ]]; then
        echo "ERROR: update extracted, but VERSION is v$new_version_after instead of v$NEWEST_UPDATE_VERSION." >&2
        return 1
    fi

    normalize_manifest_text_files
    apply_release_deletions

    reset_state_for_version \
        "$NEWEST_UPDATE_VERSION" \
        "$UPDATE_REQUIRES_BUILD" \
        "$(basename "$NEWEST_UPDATE_FILE")" \
        "$package_sha"

    echo "UPDATE: v$NEWEST_UPDATE_VERSION applied; REQUIRES_BUILD=$UPDATE_REQUIRES_BUILD"
}

state_is_prepared() {
    [[ "$STATE_TEST_DONE" == "true" ]] || return 1
    [[ "$STATE_VALIDATE_DONE" == "true" ]] || return 1

    if [[ "$STATE_REQUIRES_BUILD" == "true" ]]; then
        [[ "$STATE_BUILD_DONE" == "true" ]] || return 1
        [[ "$STATE_INSTALL_DONE" == "true" ]] || return 1
    else
        [[ "$STATE_BUILD_DONE" == "skipped" ]] || return 1
        [[ "$STATE_INSTALL_DONE" == "skipped" ]] || return 1
    fi
    return 0
}

prepare_current_version() {
    if [[ "$STATE_TEST_DONE" != "true" ]]; then
        run_step "TEST" ./dev.sh test
        STATE_TEST_DONE="true"
        save_state
    fi

    if [[ "$STATE_VALIDATE_DONE" != "true" ]]; then
        run_step "VALIDATE" ./dev.sh validate
        STATE_VALIDATE_DONE="true"
        save_state
    fi

    if [[ "$STATE_REQUIRES_BUILD" == "true" ]]; then
        if [[ "$STATE_BUILD_DONE" != "true" ]]; then
            run_step "BUILD" ./dev.sh build
            STATE_BUILD_DONE="true"
            save_state
        fi

        if [[ "$STATE_INSTALL_DONE" != "true" ]]; then
            run_step "INSTALL" ./dev.sh install
            STATE_INSTALL_DONE="true"
            save_state
        fi
    else
        if [[ "$STATE_BUILD_DONE" != "skipped" || "$STATE_INSTALL_DONE" != "skipped" ]]; then
            echo "UPDATE: v$STATE_VERSION is marked REQUIRES_BUILD=false; skipping BUILD and INSTALL."
            STATE_BUILD_DONE="skipped"
            STATE_INSTALL_DONE="skipped"
            save_state
        fi
    fi
}

print_first_time_setup_note() {
    echo
    echo "============================================================"
    echo "IMPORTANT: FIRST-TIME SETUP BEFORE AUTOMATED TESTING"
    echo "============================================================"
    echo "If an install cleared app data, complete the MiniClient first-time"
    echo "setup and reach the normal SageTV UI before running ANY MCP/player test."
    echo "Do not use a test matrix/session/search test as the setup procedure."
    echo "Scripted launch/test client ID: $SCRIPTED_CLIENT_ID (DEV001)"
    echo "Override per test with: --client-id <ID>"
}

main() {
    local current_version local_requires

    case "${1:-}" in
        help|-h|--help)
            cat <<'EOF_USAGE'
Usage: ./update.sh

Apply the highest newer validated changed-files ZIP from artifacts/downloads,
resume required test/validate/build/install preparation, then launch DEV001.
Windows: update.cmd
EOF_USAGE
            return 0
            ;;
        "") ;;
        *)
            echo "ERROR: update.sh does not accept arguments; use --help for usage." >&2
            return 2
            ;;
    esac

    current_version="$(read_current_version)"
    load_state

    # If this version was installed outside this runner (for example the v0.5.78
    # bootstrap patch), initialize a fresh resume state from its update document.
    if [[ "$STATE_VERSION" != "$current_version" ]]; then
        local_requires="$(read_local_requires_build "$current_version")"
        apply_release_deletions
        reset_state_for_version "$current_version" "$local_requires"
    fi

    apply_new_update_if_available "$current_version"

    # VERSION may have changed after extracting a newer update.
    current_version="$(read_current_version)"
    load_state
    if [[ "$STATE_VERSION" != "$current_version" ]]; then
        local_requires="$(read_local_requires_build "$current_version")"
        apply_release_deletions
        reset_state_for_version "$current_version" "$local_requires"
    fi

    if state_is_prepared; then
        echo "UPDATE: v$current_version preparation is already complete; launching only."
    else
        prepare_current_version
    fi

    run_step "LAUNCH" ./dev.sh launch --client-id "$SCRIPTED_CLIENT_ID"
    print_first_time_setup_note

    echo
    echo "============================================================"
    echo "WORKFLOW COMPLETE: v$current_version"
    echo "============================================================"
}

main "$@"
