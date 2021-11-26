# Based on image: https://github.com/adoptium/containers/blob/main/17/jdk/ubuntu/Dockerfile.releases.full
# Add libraries required by weasis build: bzip2 unzip xz-utils fakeroot rpm
FROM ubuntu:20.04

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends tzdata curl ca-certificates fontconfig locales binutils bzip2 unzip xz-utils fakeroot rpm \
    && echo "en_US.UTF-8 UTF-8" >> /etc/locale.gen \
    && locale-gen en_US.UTF-8 \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_VERSION jdk-17+35

RUN set -eux; \
    ARCH="$(dpkg --print-architecture)"; \
    case "${ARCH}" in \
       aarch64|arm64) \
         ESUM='e08e6d8c84da28a2c49ccd511f8835c329fbdd8e4faff662c58fa24cca74021d'; \
         BINARY_URL='https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17%2B35/OpenJDK17-jdk_aarch64_linux_hotspot_17_35.tar.gz'; \
         ;; \
       armhf|arm) \
         ESUM='77ef6aa6f665373e212097b937c22d0cad2add90e439ec0e90534a7ff0e8a6e9'; \
         BINARY_URL='https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17%2B35/OpenJDK17-jdk_arm_linux_hotspot_17_35.tar.gz'; \
         ;; \
       ppc64el|powerpc:common64) \
         ESUM='2e58f76fd332b73f323e47c73d0a81b76739debab067e7a32ed6abd73fd64c57'; \
         BINARY_URL='https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17%2B35/OpenJDK17-jdk_ppc64le_linux_hotspot_17_35.tar.gz'; \
         ;; \
       s390x|s390:64-bit) \
         ESUM='7a48159fca62b7f6afd58fb2e9712a3ef1494950212d4631e25598b45d9599b1'; \
         BINARY_URL='https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17%2B35/OpenJDK17-jdk_s390x_linux_hotspot_17_35.tar.gz'; \
         ;; \
       amd64|i386:x86-64) \
         ESUM='6f1335d9a7855159f982dac557420397be9aa85f3f7bc84e111d25871c02c0c7'; \
         BINARY_URL='https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17%2B35/OpenJDK17-jdk_x64_linux_hotspot_17_35.tar.gz'; \
         ;; \
       *) \
         echo "Unsupported arch: ${ARCH}"; \
         exit 1; \
         ;; \
    esac; \
    curl -LfsSo /tmp/openjdk.tar.gz ${BINARY_URL}; \
    echo "${ESUM} */tmp/openjdk.tar.gz" | sha256sum -c -; \
    mkdir -p /opt/java/openjdk; \
    cd /opt/java/openjdk; \
    tar -xf /tmp/openjdk.tar.gz --strip-components=1; \
    rm -rf /tmp/openjdk.tar.gz;

ENV JAVA_HOME=/opt/java/openjdk \
    PATH="/opt/java/openjdk/bin:$PATH"

RUN echo Verifying install ... \
    && echo javac --version && javac --version \
    && echo java --version && java --version \
    && echo Complete.

CMD ["jshell"]
