# Native LGPL source availability and reconstruction

The active APK embeds native FFmpeg code through one ExoPlayer extension AAR
and the legacy IJKPlayer AARs. This file identifies the exact distributed
artifacts, the source revisions evidenced by their binaries, and the build
scripts retained in this source release. The full LGPL 2.1 text is packaged at
`third_party/licenses/LGPL-2.1.txt`.

## ExoPlayer FFmpeg extension

Distributed artifact:

| File | SHA-256 |
| --- | --- |
| `source/dev/libs/extension-ffmpeg-2.18.0.aar` | `e9e34c833298c1177247b3f7cfef8e8be45035ff4f8076d667b8f5c9dc9c4b12` |

The Java/JNI wrapper source is ExoPlayer tag `r2.18.0`:

`https://github.com/google/ExoPlayer/tree/r2.18.0/extensions/ffmpeg`

The embedded binary reports FFmpeg revision
`839f98ff6719cf2db0cbd88cd787a1b19b9cbf47` (`n4.2.7-1-g839f98ff67`):

`https://github.com/FFmpeg/FFmpeg/tree/839f98ff6719cf2db0cbd88cd787a1b19b9cbf47`

`source/dev/exoplayer/buildffmpegext.sh` pins both revisions, uses the unified
build environment's Android NDK r21 (or downloads r21 for a standalone build),
enables the distributed decoder set, rebuilds the extension with 16 KB ELF
`LOAD` alignment, and copies the resulting AAR. The embedded configuration
disables GPL, nonfree, program, and unrelated-library features.

## IJKPlayer and its FFmpeg runtime

Distributed artifacts:

| File | SHA-256 |
| --- | --- |
| `source/dev/libs/ijkplayer-java-0.8.8-SNAPSHOT.aar` | `a3ff7dd25911989d49704480c810ac6428e9acd4d5c5e4a9f85cc88f352416bf` |
| `source/dev/libs/ijkplayer-armv7a-0.8.8-SNAPSHOT.aar` | `b56926d940f7ac519622e58abdfb889d629130b29263e580bb316e4cdab7f98f` |
| `source/dev/libs/ijkplayer-arm64-0.8.8-SNAPSHOT.aar` | `34a35fbd38928d70a0947294ff423057beca0ac79da34c2b2053b3506256153e` |
| `source/dev/libs/ijkplayer-x86-0.8.8-SNAPSHOT.aar` | `a25d604b326c40be85b047958faf87597a9989ce83a8102a9400bdf2dc1c511e` |

The native binaries report IJKPlayer `k0.8.8` and FFmpeg
`ff3.4--ijk0.8.7--20180103--001`. Corresponding upstream source is available
from:

`https://github.com/bilibili/ijkplayer/tree/k0.8.8`

That IJK tag's `init-android.sh` fetches its pinned FFmpeg fork revision.
`source/dev/ijkplayer/init-sources.sh` now checks out the exact `k0.8.8` tag,
and `source/dev/ijkplayer/build-ijk.sh` preserves the recorded NDK r13b and
`--disable-linux-perf` build inputs from the original artifact build. The
embedded configuration contains `--disable-gpl --disable-nonfree`.

The checked-in AAR names contain `SNAPSHOT` because they were copied from the
original MiniClient local Maven build. Their hashes and embedded version/config
strings are therefore authoritative; this document does not claim a byte-for-
byte match to an artifact from a repository that no longer publishes them.

## Release requirement

Keep this file, the relevant full license texts, the exact build scripts, and
the checked-in AAR hashes beside every source/APK release. If any AAR changes,
the release gate must fail until its hash, embedded configuration, exact source
revision, and reconstruction instructions are reviewed again.
