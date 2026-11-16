# Ambiente di build Android per KiwiSDR Grabber: JDK 21 (Temurin) + Android SDK a riga di comando.
# Nessun componente viene installato sul PC host: la build gira interamente nel container.
FROM eclipse-temurin:21-jdk-noble

ARG CMDLINE_TOOLS_VERSION=16111833
ARG ANDROID_PLATFORM=android-37.0
ARG ANDROID_BUILD_TOOLS=37.0.0

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/gradle-home
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip rsync curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p $ANDROID_HOME/cmdline-tools \
    && curl -fsSL -o /tmp/cmdline-tools.zip \
       https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools \
    && mv /tmp/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
    && rm -rf /tmp/cmdline-tools /tmp/cmdline-tools.zip

RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install "platform-tools" "platforms;${ANDROID_PLATFORM}" "build-tools;${ANDROID_BUILD_TOOLS}" > /dev/null

COPY build.sh /usr/local/bin/kiwi-android-build
RUN sed -i 's/\r$//' /usr/local/bin/kiwi-android-build && chmod +x /usr/local/bin/kiwi-android-build

WORKDIR /work
ENTRYPOINT ["kiwi-android-build"]
