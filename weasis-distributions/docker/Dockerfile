# Based on image: https://github.com/adoptium/containers/blob/main/26/jdk/ubuntu/resolute/Dockerfile
# Additional packages: https://github.com/adoptium/temurin26-binaries/releases/
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

ENV JAVA_VERSION=jdk-26.0.2+10

RUN set -eux; \
    ARCH="$(dpkg --print-architecture)"; \
    case "${ARCH}" in \
       amd64) \
         ESUM='56f768372f6ca1e2eb4c5f46b78f627949e8dcfe9c9723926cf45a45faf35802'; \
         BINARY_URL='https://github.com/adoptium/temurin26-binaries/releases/download/jdk-26.0.2%2B10/OpenJDK26U-jdk_x64_linux_hotspot_26.0.2_10.tar.gz'; \
         ;; \
       arm64) \
         ESUM='f82aeb1d5b0ddb93d3bd81c20948bc560c0758993157202424c17e805e2effbb'; \
         BINARY_URL='https://github.com/adoptium/temurin26-binaries/releases/download/jdk-26.0.2%2B10/OpenJDK26U-jdk_aarch64_linux_hotspot_26.0.2_10.tar.gz'; \
         ;; \
       *) \
         echo "Unsupported arch: ${ARCH}"; \
         exit 1; \
         ;; \
    esac; \
    savedAptMark="$(apt-mark showmanual)"; \
    apt-get update; \
    apt-get install -y --no-install-recommends wget gnupg; \
    wget --progress=dot:giga -O /tmp/openjdk.tar.gz ${BINARY_URL}; \
    wget --progress=dot:giga -O /tmp/openjdk.tar.gz.sig ${BINARY_URL}.sig; \
    export GNUPGHOME="$(mktemp -d)"; \
    # gpg: key 843C48A565F8F04B: "Adoptium GPG Key (DEB/RPM Signing Key) <temurin-dev@eclipse.org>" imported
    gpg --batch --keyserver keyserver.ubuntu.com --recv-keys 3B04D753C9050D9A5D343F39843C48A565F8F04B; \
    gpg --batch --verify /tmp/openjdk.tar.gz.sig /tmp/openjdk.tar.gz; \
    rm -rf "${GNUPGHOME}" /tmp/openjdk.tar.gz.sig; \
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
