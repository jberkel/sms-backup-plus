# See here for image contents: https://github.com/microsoft/vscode-dev-containers/tree/v0.166.1/containers/ubuntu/.devcontainer/base.Dockerfile

# [Choice] Ubuntu version: bionic, focal
ARG VARIANT="focal"
FROM mcr.microsoft.com/vscode/devcontainers/base:0-${VARIANT}

RUN export DEBIAN_FRONTEND=noninteractive && \
    apt-get update && apt-get -y install --no-install-recommends openjdk-8-jdk

ENV ANDROID_SDK_ROOT "/usr/lib/android-sdk"

ENV PATH "$PATH:$ANDROID_SDK_ROOT/platform-tools"

RUN mkdir -p $ANDROID_SDK_ROOT && chmod 777 $ANDROID_SDK_ROOT && \
    cd $ANDROID_SDK_ROOT && \
    wget https://dl.google.com/android/repository/platform-tools-latest-linux.zip && \
    unzip platform-tools-latest-linux.zip

RUN cd $ANDROID_SDK_ROOT && \
    mkdir cmdline-tools && cd cmdline-tools && \
    wget https://dl.google.com/android/repository/commandlinetools-linux-7302050_latest.zip && \
    unzip commandlinetools-linux-*.zip && mv cmdline-tools latest && yes | latest/bin/sdkmanager --licenses

