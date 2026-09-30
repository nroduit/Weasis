# Based on image: https://github.com/adoptium/containers/blob/main/26/jdk/ubuntu/resolute/Dockerfile
# JDK 27: the OpenJDK GA build of https://jdk.java.net/27/ (GPL+CPE), verified by its pinned
# SHA-256, until Temurin 27 is published (then switch BINARY_URL/ESUM back to
# https://github.com/adoptium/temurin27-binaries/releases/ and restore the GPG check).
# Add libraries required by weasis build: bzip2 unzip xz-utils fakeroot rpm

FROM ubuntu:26.04

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=$JAVA_HOME/bin:$PATH

# Default to UTF-8 file.encoding
ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

RUN set -eux; \
    apt-get update || (sleep 10 && apt-get update); \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends wget fontconfig ca-certificates p11-kit binutils tzdata locales curl bzip2 unzip xz-utils fakeroot rpm \
    ; \
    echo "en_US.UTF-8 UTF-8" >> /etc/locale.gen; \
    locale-gen en_US.UTF-8; \
    rm -rf /var/lib/apt/lists/*

ENV JAVA_VERSION=jdk-27+35

RUN set -eux; \
    ARCH="$(dpkg --print-architecture)"; \
    case "${ARCH}" in \
       amd64) \
         ESUM='95fc37eb3a18a27a26d5904c2d89d52bace8dafa9a078ca27f4747fbc4bf070b'; \
         BINARY_URL='https://download.java.net/java/GA/jdk27/55ce5470a6294008af0057ff4626d0e5/35/GPL/openjdk-27_linux-x64_bin.tar.gz'; \
         ;; \
       arm64) \
         ESUM='da4e9dde1fff90204739e969187bab4751bd59a2a1c479672e1a1810f7dd23ea'; \
         BINARY_URL='https://download.java.net/java/GA/jdk27/55ce5470a6294008af0057ff4626d0e5/35/GPL/openjdk-27_linux-aarch64_bin.tar.gz'; \
         ;; \
       *) \
         echo "Unsupported arch: ${ARCH}"; \
         exit 1; \
         ;; \
    esac; \
    savedAptMark="$(apt-mark showmanual)"; \
    apt-get update; \
    apt-get install -y --no-install-recommends wget; \
    wget --progress=dot:giga --tries=3 --timeout=30 -O /tmp/openjdk.tar.gz ${BINARY_URL}; \
    # jdk.java.net publishes SHA-256 sums only (no detached signature): the pinned sum is the
    # integrity check.
    echo "${ESUM} */tmp/openjdk.tar.gz" | sha256sum -c -; \
    mkdir -p "$JAVA_HOME"; \
    tar --extract \
        --file /tmp/openjdk.tar.gz \
        --directory "$JAVA_HOME" \
        --strip-components 1 \
        --no-same-owner \
    ; \
    rm -f /tmp/openjdk.tar.gz ${JAVA_HOME}/lib/src.zip; \
    apt-mark auto '.*' > /dev/null; \
    apt-mark manual $savedAptMark > /dev/null; \
    apt-get purge -y --auto-remove -o APT::AutoRemove::RecommendsImportant=false; \
    rm -rf /var/lib/apt/lists/*; \
    # https://github.com/docker-library/openjdk/issues/331#issuecomment-498834472
    find "$JAVA_HOME/lib" -name '*.so' -exec dirname '{}' ';' | sort -u > /etc/ld.so.conf.d/docker-openjdk.conf; \
    ldconfig; \
    # https://github.com/docker-library/openjdk/issues/212#issuecomment-420979840
    # https://openjdk.java.net/jeps/341
    java -Xshare:dump;

RUN set -eux; \
    echo "Verifying install ..."; \
    fileEncoding="$(echo 'System.out.println(System.getProperty("file.encoding"))' | jshell -s -)"; [ "$fileEncoding" = 'UTF-8' ]; rm -rf ~/.java; \
    echo "javac --version"; javac --version; \
    echo "java --version"; java --version; \
    echo "Complete."
CMD ["jshell"]
