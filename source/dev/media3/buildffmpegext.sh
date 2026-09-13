#!/usr/bin/env bash

# Rebuild the exact Media3 FFmpeg audio fallback distributed by this project.
# This is component-only: it does not rebuild a server or Docker image and it
# does not enable Media3's experimental FFmpeg video renderer.
set -euo pipefail

MEDIA3_VERSION="1.11.0"
MEDIA3_COMMIT="2bc207851df311340767e913931ca7b28cab1794"
FFMPEG_COMMIT="ba69be84a1ceabfb39127831ad8da0fd7cb471f3"
ANDROID_ABI="21"
HOST_PLATFORM="linux-x86_64"
ENABLED_DECODERS=(aac mp3 ac3 eac3 truehd dca vorbis opus flac alac pcm_mulaw pcm_alaw)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEV_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BUILD_ROOT="${SCRIPT_DIR}/build"
MEDIA3_ROOT="${BUILD_ROOT}/androidx-media"
FFMPEG_ROOT="${BUILD_ROOT}/ffmpeg"
MODULE_MAIN="${MEDIA3_ROOT}/libraries/decoder_ffmpeg/src/main"
OUTPUT_AAR="${DEV_ROOT}/libs/extension-media3-ffmpeg-${MEDIA3_VERSION}.aar"
NDK_PATH="${ANDROID_NDK_HOME:-/opt/android-sdk/ndk/26.1.10909125}"

if [[ -n "${OPENSAGETV_VIBE_ANDROID_JAVA_HOME:-}" ]]; then
    export JAVA_HOME="${OPENSAGETV_VIBE_ANDROID_JAVA_HOME}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
fi

if [[ ! -x "${NDK_PATH}/toolchains/llvm/prebuilt/${HOST_PLATFORM}/bin/clang" ]]; then
    echo "ERROR: Android NDK was not found at ${NDK_PATH}" >&2
    exit 1
fi

mkdir -p "${BUILD_ROOT}"

if [[ ! -d "${MEDIA3_ROOT}/.git" ]]; then
    git clone https://github.com/androidx/media.git "${MEDIA3_ROOT}"
fi
git -C "${MEDIA3_ROOT}" fetch origin "${MEDIA3_COMMIT}"
git -C "${MEDIA3_ROOT}" checkout --detach "${MEDIA3_COMMIT}"
if [[ "$(git -C "${MEDIA3_ROOT}" rev-parse HEAD)" != "${MEDIA3_COMMIT}" ]]; then
    echo "ERROR: Media3 checkout does not match ${MEDIA3_COMMIT}" >&2
    exit 1
fi

if [[ ! -d "${FFMPEG_ROOT}/.git" ]]; then
    git clone https://github.com/FFmpeg/FFmpeg.git "${FFMPEG_ROOT}"
fi
git -C "${FFMPEG_ROOT}" fetch origin "${FFMPEG_COMMIT}"
git -C "${FFMPEG_ROOT}" checkout --detach "${FFMPEG_COMMIT}"
if [[ "$(git -C "${FFMPEG_ROOT}" rev-parse HEAD)" != "${FFMPEG_COMMIT}" ]]; then
    echo "ERROR: FFmpeg checkout does not match ${FFMPEG_COMMIT}" >&2
    exit 1
fi

ln -sfn "${FFMPEG_ROOT}" "${MODULE_MAIN}/jni/ffmpeg"
"${MODULE_MAIN}/jni/build_ffmpeg.sh" \
    "${MODULE_MAIN}" "${NDK_PATH}" "${HOST_PLATFORM}" "${ANDROID_ABI}" \
    "${ENABLED_DECODERS[@]}"

(
    cd "${MEDIA3_ROOT}"
    ./gradlew :lib-decoder-ffmpeg:assembleRelease
)

BUILT_AAR="${MEDIA3_ROOT}/libraries/decoder_ffmpeg/buildout/outputs/aar/lib-decoder-ffmpeg-release.aar"
if [[ ! -f "${BUILT_AAR}" ]]; then
    echo "ERROR: Media3 FFmpeg build did not create ${BUILT_AAR}" >&2
    exit 1
fi

# Legacy ExoPlayer also loads libffmpegJNI.so. Give Media3's independent JNI
# library a unique name so Android cannot bind the wrong native methods when
# both player generations are present in one process.
PACKAGE_ROOT="${BUILD_ROOT}/aar-package"
case "${PACKAGE_ROOT}" in
    "${BUILD_ROOT}"/*) ;;
    *) echo "ERROR: unsafe package directory ${PACKAGE_ROOT}" >&2; exit 1 ;;
esac
rm -rf "${PACKAGE_ROOT}"
mkdir -p "${PACKAGE_ROOT}"
(
    cd "${PACKAGE_ROOT}"
    unzip -q "${BUILT_AAR}"
    while IFS= read -r -d '' library; do
        mv "${library}" "${library%/*}/libmedia3ffmpegJNI.so"
    done < <(find jni -type f -name libffmpegJNI.so -print0)
    zip -q -r "${OUTPUT_AAR}" .
)

sha256sum "${OUTPUT_AAR}"
