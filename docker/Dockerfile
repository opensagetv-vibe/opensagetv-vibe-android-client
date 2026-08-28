FROM eclipse-temurin:8-jdk-jammy AS legacy-jdk8

FROM eclipse-temurin:17-jdk-jammy

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

RUN python3 -m pip install --no-cache-dir --upgrade pip setuptools wheel \
    && python3 -m pip install --no-cache-dir 'mcp[cli]>=2,<3' 'tomli>=2'

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

COPY docker/entrypoint.sh /usr/local/bin/sagetv-dev
RUN chmod +x /usr/local/bin/sagetv-dev \
    && mkdir -p /workspace /gradle-cache /root/.android

WORKDIR /workspace
ENTRYPOINT ["/usr/local/bin/sagetv-dev"]
CMD ["shell"]
