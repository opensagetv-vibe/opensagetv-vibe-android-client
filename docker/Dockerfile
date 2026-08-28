FROM eclipse-temurin:8-jdk-jammy@sha256:7cb1137d4a02aeb7ca85faae69c5f0720703936cbfb0b4af21067c73174f9b5e AS legacy-jdk8

FROM eclipse-temurin:17-jdk-jammy@sha256:400014962ad7224461f945bb1cc3d7d5a1927ce15b8245b72d9cedcda554cd2a

LABEL org.opencontainers.image.title="OpenSageTV Vibe Android Client development environment" \
      org.opencontainers.image.description="Android, Gradle, ADB, test, and MCP toolchain for OpenSageTV Vibe" \
      org.opencontainers.image.source="https://github.com/opensagetv-vibe/opensagetv-vibe-android-client"

# Keep JDK 8 available for the untouched Gradle 6.1.1 / AGP 4.0.2 baseline.
COPY --from=legacy-jdk8 /opt/java/openjdk /opt/java/jdk8

ARG DEBIAN_FRONTEND=noninteractive
ARG ANDROID_SDK_TOOLS_URL=https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip

ENV ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk \
    GRADLE_USER_HOME=/gradle-cache \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH=/opt/android-sdk/platform-tools:/opt/android-sdk/build-tools/36.0.0:/opt/android-sdk/cmdline-tools/latest/bin:${PATH}

RUN apt-get update && apt-get install -y --no-install-recommends \
      ca-certificates \
      curl \
      git \
      grep \
      iproute2 \
      iputils-ping \
      jq \
      libc6-i386 \
      lib32gcc-s1 \
      lib32stdc++6 \
      lib32z1 \
      procps \
      python3 \
      python3-pip \
      python3-venv \
      unzip \
    && rm -rf /var/lib/apt/lists/*

RUN python3 -m pip install --no-cache-dir --upgrade \
      'pip==26.2.1' \
      'setuptools==84.0.0' \
      'wheel==0.48.0' \
    && python3 -m pip install --no-cache-dir \
      'mcp[cli]==2.1.1' \
      'tomli==2.4.1'

RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools \
    && curl -fsSL "${ANDROID_SDK_TOOLS_URL}" -o /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp/android-cmdline \
    && mv /tmp/android-cmdline/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest \
    && rm -rf /tmp/android-cmdline /tmp/cmdline-tools.zip

RUN yes | sdkmanager --licenses >/dev/null || true \
    && sdkmanager \
      "platform-tools" \
      "platforms;android-29" \
      "build-tools;29.0.2" \
      "platforms;android-36" \
      "build-tools;36.0.0" \
      "ndk;21.0.6113669"

COPY docker/entrypoint.sh /usr/local/bin/opensagetv-vibe-android-dev
RUN chmod +x /usr/local/bin/opensagetv-vibe-android-dev \
    && ln -s /usr/local/bin/opensagetv-vibe-android-dev /usr/local/bin/sagetv-dev \
    && mkdir -p /workspace /gradle-cache /root/.android

WORKDIR /workspace
ENTRYPOINT ["/usr/local/bin/opensagetv-vibe-android-dev"]
CMD ["shell"]
