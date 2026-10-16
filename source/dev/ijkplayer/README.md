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
