# Third-party notices

This document identifies the runtime component families distributed with the
OpenSageTV Vibe Android Client. All 122 selected Maven coordinates are listed
in `third_party/RUNTIME_DEPENDENCIES.csv`; graph and security-scan evidence is
described in `docs/DEPENDENCY_AUDIT.md`. License names below come
from the versioned upstream source, published Maven metadata, or—where noted—
the configuration embedded in the checked-in native binary.

This notice is informational and does not replace the license terms supplied
by each project.

## Java and Android runtime components

| Component | Distributed version | License / notice source |
| --- | --- | --- |
| AndroidX AppCompat, CardView, Legacy Support, Leanback, Preference, RecyclerView, and Media3 | Resolved locked graph; Media3 1.11.0 | Apache License 2.0; AndroidX source and published artifacts |
| Legacy Google ExoPlayer modules | 2.18.1 | Apache License 2.0; ExoPlayer release source |
| GSYVideoPlayer Java and Exo2 modules | 13.1.0 | Apache License 2.0; versioned upstream `LICENSE` |
| libGDX core, Android backend, and native platform artifacts | 1.9.14 | Apache License 2.0; libGDX release source |
| Guava Android/JRE | Resolved 33.3.1-android | Apache License 2.0; Guava published artifact |
| SMBJ | 0.13.0 | Apache License 2.0; published POM and upstream source |
| Glide | 3.8.0 | BSD-3-Clause for Glide, with Apache-2.0, MIT, and retained third-party terms itemized in Glide's versioned `LICENSE` |
| SLF4J API | 1.7.6 | MIT License; versioned upstream `LICENSE.txt` |
| Logback Android Core and Classic | 1.1.1-6 | Eclipse Public License 1.0 or GNU LGPL 2.1; published POM |
| CircularProgressView | 2.4.0 | MIT License; published POM |
| JetBrains annotations | 16.0.2 | Apache License 2.0; published artifact |
| JZlib | 1.1.3 | BSD-style JCraft license; published POM and upstream `LICENSE.txt` |
| NanoHTTPD | 2.2.0 | BSD-3-Clause / Modified BSD; versioned upstream source |

The former Ostermiller Java Utilities dependency is not distributed. Its sole
use, the Push transport circular byte buffer, was replaced by the project's
Apache-2.0 `BoundedCircularByteBuffer` implementation.

## Integrated DVD presentation core

The platform-neutral DVD SPU assembler, decoder, compositor, palette helpers,
physical audio-stream decoder, diagnostics, and their deterministic regression
fixtures are derived from **SageTV Android DVD Presentation Engine v2.1**,
distributed under the Apache License 2.0. The reviewed source archive has
SHA-256
`7B9BF785F4547C245F3C313DAA86831A028152882739CA41AFFF6E5321053D4D`.
OpenSageTV Vibe supplies an Android adapter that retains the physically proven
SageTV STV subtitle On/Off wire semantics and the existing Media3 DVD transport,
clock, Surface, and drain lifecycle.

The upstream package's notice is retained here verbatim:

> SageTV Android DVD Presentation Engine
>
> This clean-room Java implementation is designed to interoperate with the
> Apache-licensed SageTV legacy MiniDVDPlayer protocol. libdvdnav, FFmpeg and
> GStreamer were used only to compare externally observable DVD format behavior;
> no source from those projects is included in this distribution.

The repository's Apache License 2.0 text applies to this integrated source.
No libdvdnav, FFmpeg, or GStreamer source was copied into the Java decoder.

## Checked-in native playback artifacts

| Artifact | Included runtime | License evidence and boundary |
| --- | --- | --- |
| `extension-ffmpeg-2.18.0.aar` | Legacy Exo FFmpeg audio decoder extension and FFmpeg `libavcodec`/`libavutil`/`libswresample` subset | The Java/JNI extension follows ExoPlayer's Apache-2.0 terms. The embedded FFmpeg configuration enables only the required audio decoders, disables programs and unrelated libraries, and contains no `--enable-gpl` or `--enable-nonfree`; the FFmpeg portion therefore retains LGPL 2.1-or-later obligations. |
| `ijkplayer-java-0.8.8-SNAPSHOT.aar` and ABI AARs | IJKPlayer, IJK SDL layer, and its FFmpeg 3.4-era runtime | IJKPlayer is LGPL 2.1-or-later. Its upstream notice also identifies FFmpeg/libVLC/SoundTouch under LGPL-compatible terms, SDL under the zlib license, libyuv under BSD/ISC terms, and the optional Exo adapter under Apache-2.0. The exact embedded ARM64 FFmpeg configuration explicitly contains `--disable-gpl --disable-nonfree`; the example profiler and demo code identified by upstream as GPL/unknown are not packaged by these AAR declarations. |

The older `extension-ffmpeg-2.14.0` through `2.17.1` files and the unused
`ijkplayer-exo` AAR remain checked-in migration material but are not declared
by the active runtime build and are not packaged in the APK.

## Redistribution notes

- Preserve this file with source and binary release packages.
- Preserve the full texts under `third_party/licenses/`, the exact coordinate
  inventory, and `third_party/source-offers/README.md` with every public source
  and APK release.
- Preserve Glide's component-specific copyright and license sections rather
  than labeling the whole artifact only as BSD.
- Re-extract and review the embedded FFmpeg configuration whenever either
  checked-in native AAR changes. Enabling a GPL or nonfree component changes
  the redistribution boundary and must fail release review.
- The OpenSageTV Vibe project does not grant trademark rights for any listed
  third-party project.
