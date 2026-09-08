#!/usr/bin/env bash

if [ ! -d ijkplayer ] ; then
    echo "Fetching IJKPlayer Sources"
    git clone https://github.com/Bilibili/ijkplayer.git
fi

echo "Checking out the source version embedded in the distributed 0.8.8 AARs"
git -C ijkplayer fetch --tags origin
git -C ijkplayer checkout --detach k0.8.8
if [ "$(git -C ijkplayer describe --tags --exact-match 2>/dev/null)" != "k0.8.8" ]; then
    echo "ERROR: IJKPlayer checkout is not exactly k0.8.8" >&2
    exit 1
fi

if [ ! -d Ndk ] ; then
    echo "Setting up NDK"
    NDK=r13b
    mkdir Ndk
    cd Ndk/
    # wget http://dl.google.com/android/ndk/android-ndk-${NDK}-linux-x86_64.bin
    wget https://dl.google.com/android/repository/android-ndk-${NDK}-linux-x86_64.zip
    #chmod 755 android-ndk-${NDK}-linux-x86_64.bin
    #./android-ndk-${NDK}-linux-x86_64.bin
    unzip ./android-ndk-${NDK}-linux-x86_64.zip
    cd ..
fi



