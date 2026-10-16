#!/usr/bin/env python3
"""Rebuild only the IJK player glue against unchanged, pinned FFmpeg/SDL AARs.

Run in the unified SDK container. Disposable downloads/source/object files use
the explicitly supplied system-temp build directory. The output AARs retain
every original entry except libijkplayer.so; Java, SDL and FFmpeg are unchanged.
No automatic replacement of authoritative AARs occurs.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCES = {
    'player': ('Bilibili/ijkplayer', 'k0.8.8', 'ef54cec44fc9f7f0515a48b32c1f1a80aef4e76887316dda84e7ca1d2e4c09b0'),
    'ffmpeg': ('Bilibili/FFmpeg', 'ff3.4--ijk0.8.7--20180103--001', '58f01eaa93c91db3ed42a687f2489dcd937ecd0f443c261fb976fac30d114ad9'),
    'soundtouch': ('Bilibili/soundtouch', '6bf39cd3bf6b0c156267d12446b0d6bdcfcd53c2', 'fbe2db43f049f84addacc34ff32c51116f15a2536f7269445c425ff284d691f4'),
}

def digest(path):
    return hashlib.file_digest(path.open('rb'), 'sha256').hexdigest()

def run(command, cwd, environment=None):
    print(' '.join(map(str, command)), flush=True)
    subprocess.run(list(map(str, command)), cwd=cwd, env=environment, check=True)

def source(name, build):
    repo, ref, expected = SOURCES[name]
    archive = build / (name + '.tar.gz')
    if not archive.exists():
        urllib.request.urlretrieve(f'https://codeload.github.com/{repo}/tar.gz/{ref}', archive)
    if digest(archive) != expected:
        raise RuntimeError(f'{name} source hash mismatch')
    destination = build / name
    destination.mkdir(exist_ok=True)
    with tarfile.open(archive) as payload:
        # Upstream archive includes legacy absolute SDK/profiler symlinks.
        # This build names real sources explicitly; do not extract any links.
        payload.extractall(destination, members=[m for m in payload.getmembers()
                           if m.isfile() or m.isdir()], filter='data')
    return next(p for p in destination.iterdir() if p.is_dir())

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-dir', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    build = args.build_dir.resolve()
    if build == Path('/tmp') or Path('/tmp') not in build.parents or build.is_symlink():
        raise RuntimeError('An owned child of /tmp is required; never the temp root')
    build.mkdir(parents=True, exist_ok=True)
    output = args.output_dir.resolve(); output.mkdir(parents=True, exist_ok=True)
    player, ffmpeg, soundtouch = (source(name, build) for name in SOURCES)
    shutil.copytree(soundtouch, player / 'ijkmedia/ijksoundtouch', dirs_exist_ok=True)
    patch = ROOT / 'source/dev/ijkplayer/native/software-deinterlace.patch'
    # Extraction restores the pinned files before every build; apply the
    # reviewed patch every time, including retries after a toolchain error.
    run(['patch', '-p1', '--fuzz=0', '-i', patch], player)
    shutil.copyfile(ROOT / 'source/dev/ijkplayer/native/vibe_deinterlace.h',
                    player / 'ijkmedia/ijkplayer/vibe_deinterlace.h')
    ndk = Path(os.environ.get('ANDROID_NDK_HOME', '/opt/android-sdk/ndk/21.0.6113669'))
    toolchain = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin'
    records = []
    for short, abi, triple, arch in (
        ('armv7a', 'armeabi-v7a', 'armv7a-linux-androideabi', 'arm'),
        ('arm64', 'arm64-v8a', 'aarch64-linux-android', 'aarch64'),
    ):
        target = build / short; target.mkdir(exist_ok=True)
        original = ROOT / f'source/dev/libs/ijkplayer-{short}-0.8.8-SNAPSHOT.aar'
        with zipfile.ZipFile(original) as payload:
            for library in ('libijkffmpeg.so', 'libijksdl.so'):
                (target / library).write_bytes(payload.read(f'jni/{abi}/{library}'))
        env = os.environ.copy(); env['PATH'] = str(toolchain) + ':' + env['PATH']
        run([ffmpeg / 'configure', '--enable-cross-compile', '--target-os=android',
             f'--arch={arch}', f'--cc={toolchain}/{triple}23-clang',
             '--disable-asm', '--disable-programs', '--disable-doc', '--disable-everything',
             '--disable-avdevice', '--enable-swscale',
             '--enable-avfilter', '--enable-filter=yadif', '--disable-gpl', '--disable-nonfree'], ffmpeg, env)
        (ffmpeg / 'libffmpeg').mkdir(exist_ok=True)
        shutil.copyfile(ffmpeg / 'config.h', ffmpeg / 'libffmpeg/config.h')
        # Generated build inputs; authoritative source is the pinned source and
        # reviewed patch above. Existing player sources/JNI stay at k0.8.8.
        makefile = target / 'Android.mk'
        makefile.write_text(f'''LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := ijkffmpeg
LOCAL_SRC_FILES := {target}/libijkffmpeg.so
include $(PREBUILT_SHARED_LIBRARY)
include $(CLEAR_VARS)
LOCAL_MODULE := ijksdl
LOCAL_SRC_FILES := {target}/libijksdl.so
include $(PREBUILT_SHARED_LIBRARY)
MY_APP_FFMPEG_INCLUDE_PATH := {ffmpeg}
include {player}/ijkprof/android-ndk-profiler-dummy/jni/Android.mk
include {soundtouch}/Android.mk
include {player}/ijkmedia/ijkplayer/Android.mk
''')
        application = target / 'Application.mk'
        application.write_text(f'''APP_PLATFORM := android-23
APP_ABI := {abi}
APP_STL := c++_static
APP_OPTIM := release
APP_CFLAGS := -O3 -DANDROID -DNDEBUG -D__STDC_CONSTANT_MACROS -Wno-error
APP_LDFLAGS := -Wl,-z,max-page-size=16384
''')
        run([ndk / 'ndk-build', f'NDK_PROJECT_PATH={target}', f'APP_BUILD_SCRIPT={makefile}',
             f'NDK_APPLICATION_MK={application}', '-j4'], target, env)
        library = target / 'libs' / abi / 'libijkplayer.so'
        candidate = output / original.name
        # Copy every non-player entry bit-for-bit; never rebuild SDL or FFmpeg.
        with zipfile.ZipFile(original) as before, zipfile.ZipFile(candidate, 'w') as after:
            for entry in before.infolist():
                after.writestr(entry, library.read_bytes() if entry.filename == f'jni/{abi}/libijkplayer.so'
                               else before.read(entry.filename))
        records.append({'abi': abi, 'baseline': digest(original), 'candidate': digest(candidate),
                        'player': digest(library), 'ffmpeg': digest(target / 'libijkffmpeg.so'),
                        'sdl': digest(target / 'libijksdl.so')})
    (output / 'native-build.json').write_text(json.dumps({'sources': SOURCES, 'artifacts': records}, indent=2) + '\n')

if __name__ == '__main__':
    main()
