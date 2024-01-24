# Based on image: https://github.com/adoptium/containers/blob/main/21/jdk/ubuntu/jammy/Dockerfile.releases.full
# Additional packages: https://github.com/adoptium/temurin21-binaries/releases
# Add libraries required by weasis build: bzip2 unzip xz-utils fakeroot rpm
FROM ubuntu:22.04

ENV JAVA_HOME /opt/java/openjdk
ENV PATH $JAVA_HOME/bin:$PATH

# Default to UTF-8 file.encoding
ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends tzdata curl wget ca-certificates fontconfig locales p11-kit binutils bzip2 unzip xz-utils fakeroot rpm \
    && echo "en_US.UTF-8 UTF-8" >> /etc/locale.gen \
    && locale-gen en_US.UTF-8 \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_VERSION jdk-21.0.1+12

RUN set -eux; \
    ARCH="$(dpkg --print-architecture)"; \
    case "${ARCH}" in \
       aarch64|arm64) \
         ESUM='e184dc29a6712c1f78754ab36fb48866583665fa345324f1a79e569c064f95e9'; \
         BINARY_URL='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.1%2B12/OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.1_12.tar.gz'; \
         ;; \
       amd64|i386:x86-64) \
         ESUM='1a6fa8abda4c5caed915cfbeeb176e7fbd12eb6b222f26e290ee45808b529aa1'; \
         BINARY_URL='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.1%2B12/OpenJDK21U-jdk_x64_linux_hotspot_21.0.1_12.tar.gz'; \
         ;; \
       armhf|arm) \
         ESUM='ea1368e420a9e2501dfd5c987b1d5e84895089c793661d75430cd2cad1c858a3'; \
         BINARY_URL='https://github.com/adoptium/temurin21-binaries/releases/download/jdk21u-2023-11-08-15-55-beta/OpenJDK21U-jdk_arm_linux_hotspot_2023-11-08-15-55.tar.gz'; \
         ;; \
       *) \
         echo "Unsupported arch: ${ARCH}"; \
         exit 1; \
         ;; \
    esac; \
    wget --progress=dot:giga -O /tmp/openjdk.tar.gz ${BINARY_URL}; \
    echo "${ESUM} */tmp/openjdk.tar.gz" | sha256sum -c -; \
	  mkdir -p "$JAVA_HOME"; \
	  tar --extract \
	      --file /tmp/openjdk.tar.gz \
	      --directory "$JAVA_HOME" \
	      --strip-components 1 \
	      --no-same-owner \
	  ; \
    rm -f /tmp/openjdk.tar.gz ${JAVA_HOME}/lib/src.zip; \
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