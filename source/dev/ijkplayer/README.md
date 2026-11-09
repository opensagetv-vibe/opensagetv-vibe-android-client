# IJK native source and builds

The current Vibe ARM artifacts use a narrowly patched k0.8.8 player library.
Rebuild it in the existing unified container with:

```text
python3 scripts/build_ijk_deinterlace.py --build-dir /tmp/vibe-ijk-player-build --output-dir artifacts/active/INTERLACE-002/native-build
```

The command pins/hashes source archives, rejects archive links, and writes
candidate AARs only. It preserves all existing AAR entries except libijkplayer.so;
FFmpeg/SDL/Java are not upgraded. Review ABI symbols, strict checksums, source
offers and affected physical gates before replacing authoritative artifacts.
The hook uses existing LGPL YADIF for marked SD interlaced software frames,
and bounded first-field spatial interpolation for 8-bit planar420 HD after
physical testing proved temporal YADIF could not meet AFTMM's frame budget.
The Video filters child selects Auto (that adaptive rule), Fast (first-field
interpolation where supported) or YADIF (temporal, frame-rate output). Forcing
YADIF on HD may fall behind on older CPUs; hardware/progressive bypass every
choice. Actual filter identity is reported from the native transition callback.
The HD tradeoff reduces vertical detail; both preserve frame PTS/rate. Seek
history reset and EOF drain apply to the temporal path; hardware paths and
legacy global CONFIG_AVFILTER/audio filtering remain unchanged. The shared
Video settings Deinterlace Auto/Off/Client/Fixed-only MIM policy controls this
hook; the older IJK Auto/Off key remains a compatibility default until the
shared preference is set. Other players retain device-controlled field output.

The same narrow player patch preserves demuxed Annex-B H.264/HEVC parameter
sets as MediaCodec `csd-0`. This allows an older hardware decoder to initialize
when an MPEG-TS read begins mid-GOP, before the next in-band SPS/PPS/VPS repeat.
It does not rewrite packets or alter the existing AVC/HEVC configuration-record
path.

The historical full-build instructions below reconstruct the unpatched baseline,
not the Vibe software-field hook. Do not overwrite patched AARs with that build.

EXT-013 also applies `native/raw-cea.patch` plus `native/vibe_cea_packet.h`.
It forwards actual software AVFrame `AV_FRAME_DATA_A53_CC` bytes before dropping
or filtering pictures. It does not OCR/render text or upgrade FFmpeg/SDL/Java.
The native queue caps outstanding total messages at128 and each payload at93
bytes, frees aborted/OOM packets, and rejects obsolete decoder serials at JNI
dispatch. PTS normalization matches IJK's existing native presentation getter.
The Java player lease additionally fences seek/flush/replacement callbacks and
clocks the existing raw event225 bridge independently of STV redraws. Only real
observed content adds a caption-format entry; languages/services stay unknown.
`native/vibe_cea_jni.h` calls the pinned existing native-invoke listener directly
for this one Vibe event, avoiding the original gateway's per-packet INFO log.
All normal native HTTP/control invoke events keep their existing gateway.
`native/push-flush.patch` recognizes the existing Java LONG_MAX flush sentinel
(truncated to-1 in IJK's32-bit seek message), only for the explicit qualified
software Push input option. It discards old AVIO/parser/decoder/audio buffers
without repositioning the server-owned FIFO, advances decoder serials, and
completes the flush. Pre-prepare Flush resumes caption admission on actual
prepare because IJK rejects pre-prepare seeks without a completion callback.
This path never requests a producer/file Seek0; hardware/other ABIs stay native.

EXT-016's qualified ARM Software implementation applies `native/paused-step.patch` and
`native/vibe_paused_step.h`. Only explicit software ARM Push can request one
forward decoder frame. Completion follows successful SDL output of a newer
frame, with request/decoder serial fences, rather than advancing a clock at
acceptance. Audio stays paused; old queued PCM is flushed and trimmed to that
actual video frame before resuming. Reverse stepping is not claimed. The
ordinary Push FRAME_STEP protocol flag remains off until its separate paused
decode-to contract is qualified; debug command acceptance alone is not a gate.
Run scripts/verify_ijk_native_candidate.py against a NEW candidate directory
before replacing AARs or updating strict verification pins. No Java ABI,
FFmpeg/SDL binary, MiniClient event, or proprietary firmware code is added.
Hardware MediaCodec does not pass software AVFrames through this function;
do not advertise hardware IJK CEA support or infer captions from format guesses.
`native/presentation-clock.patch` and `native/vibe_push_presentation.h` add
actual output-epoch proof and paused displayed-video PTS, not a synthetic clock.
An old decoder serial cannot prove a new epoch. Normal repeated-Pause Push/
Fixed, held pause, resume and affected retained-file/native clock gates pass;
compact EXT-013/016 results retain exact limits and native ABI/build hashes.
Hardware/DVD/reverse are not qualified. EXT-010 cache seeking remains separate.

EXT-018 adds default-off `hardware-step.patch`/`vibe-hardware-step` solely for
an isolated nonpersistent commissioning trial. The native request additionally
requires actual FFP_PROPV_DECODER_MEDIACODEC, rather than trusting a hardware
preference. It reuses successful SDL output/serial/request proof; hardware must
not enable `vibe-push-input` or pretend AVFrame A53 captions are available.
Build/ABI output alone does not qualify hardware stepping or its bundled
FRAME_STEP protocol contract. Existing software behavior remains independently
qualified; physical hardware acceptance is tracked under EXT-018.

Step 1 - Initialize the Sources
./init-sources.sh

Step 2 - Build the player
./build-player.sh

Step 3 - Rebuild Player and update Project with new player artifacs
./copy-resources.sh

Step 3 is the only step that needs to be done if you are modifying IJKPlayer sources.

JUST FOR Reference...
```
 2030  git clone git@github.com:stuckless/ijkplayer.git
 2031  cd ijkplayer
 2033  git remote -v
 2034  git remote add upstream https://github.com/Bilibili/ijkplayer.git
 2035  git remote -v
 2036  git fetch upstream
 2037  git rebase upstream/master
 2038  git push
 2040  sudo dpkg-reconfigure dash

 2041  cd
 2042  cd Android
 2044  mkdir Ndk
 2045  cd Ndk/
 2046  wget http://dl.google.com/android/ndk/android-ndk-r10e-linux-x86_64.bin
 2047  chmod 755 android-ndk-r10e-linux-x86_64.bin
 2048  ./android-ndk-r10e-linux-x86_64.bin

 2050  cd
 2051  cd git/ijkplayer
 2052  export ANDROID_NDK=/home/sls/Android/Ndk/android-ndk-r10e
 2053  export ANDROID_SDK=/home/sls/Android/Sdk/
 2054  cd config/
 2055  ls
 2056  ls -al
 2057  less module-default.sh
 2058  ln -sf module-default.sh module.sh
 2059  ls -al
 2060  cd ..
 2062  ./init-android.sh
 2063  cd android/contrib/
 2064  ./compile-ffmpeg.sh clean
 2065  ./compile-ffmpeg.sh all
 2066  cd ..
  2067  ./compile-ijk.sh all
 2069  cd ..
 2071  cd android/
 2073  cd ijkplayer/
 2075  less build.gradle
 2076  cd ..
 2078  cd ijkplayer/
 2079  ./gradlew
 2081  cp ~/git/sagetv-miniclient/local.properties .
 2082  ./gradlew
 2083  vim build.gradle
 2084  ./gradlew
 2086  find . -iname *.so
 2088  cd ijkplayer-sample/
 2090  cd ..
 2092  less ijkplayer-sample/build.gradle
 2096  ./gradlew assemble
 
 # use copyresources to build/update mavelLocal
 ```

if ffmpeg fails to build is linux perf error, then edit modules-defaults.sh
```
export COMMON_FF_CFG_FLAGS="$COMMON_FF_CFG_FLAGS --disable-linux-perf"
```
