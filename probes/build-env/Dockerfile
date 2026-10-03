# NON-PRODUCTION build-environment image (M0-008L-F).
# This image is a TOOLCHAIN ONLY. It contains no Hermes code, no Android app
# code and no production runtime. It exists purely to make the
# cryptography==50.0.1 native build reproducible.
#
# WHY THIS EXISTS
#   M0-008L-E failed because `docker run --rm` discarded the extracted host
#   Python between the provisioning step and the configure step
#   ("invalid or missing build python binary /opt/python314/bin/python3").
#   Everything needed is therefore baked into ONE image so that any later
#   stage is a separate, independent invocation against the same filesystem.
#
# PINNED INPUTS (all verified in docs/M0-008L-F_REPRODUCIBLE_BUILD_ENVIRONMENT.md)
#   base            ubuntu:24.04
#   host CPython    3.14.0  (astral-sh/python-build-standalone, tag 20251014)
#                   sha256 74d4516a64abc63ae4bcbffb35482879a85b7faa187fcfa47c1ca8f00faebf5f
#   Rust            1.99.0
#   Android NDK     r27c (27.2.12479018)  -> mounted from named volume, see README
#   OpenSSL         3.0.18 (beeware/cpython-android-source-deps, openssl-3.0.18-0)
#                   == the exact tree Chaquopy 17.0 ships
#   CPython source  3.14.0
#                   sha256 88d2da4eed42fa9a5f42ff58a8bc8988881bd6c547e297e46682c2687638a851
FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive
ENV PATH=/opt/python314/bin:/opt/cargo/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
ENV LD_LIBRARY_PATH=/openssl/lib:/opt/python314/lib
ENV OPENSSL_DIR=/build/work/ossl
ENV PYO3_CROSS_LIB_DIR=/build/work/pydev/lib
ENV PYO3_CROSS_PYTHON_VERSION=3.14
ENV CARGO_HOME=/opt/cargo
ENV RUSTUP_HOME=/opt/rustup
# Rust needs an explicit Android std target for the aarch64-linux-android triple.
ENV CARGO_BUILD_TARGET=aarch64-linux-android
ENV CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=/opt/android/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang

# --- host build dependencies -------------------------------------------------
RUN apt-get update -qq && apt-get install -y -qq --no-install-recommends \
      autoconf automake bison build-essential ca-certificates curl file flex \
      gperf libbz2-dev libffi-dev liblzma-dev libncurses-dev libreadline-dev \
      libsqlite3-dev libssl-dev m4 patch pkg-config tk-dev uuid-dev \
      xz-utils zlib1g-dev \
 && rm -rf /var/lib/apt/lists/*

# --- host CPython 3.14.0 (build interpreter required by CPython 3.14 configure)
# Pinned URL + pinned SHA256. Not the moving 3.14 branch, not 3.14.8.
RUN set -e; \
    cd /tmp; \
    curl -fsSL -o hostpy.tar.gz \
      "https://github.com/astral-sh/python-build-standalone/releases/download/20251014/cpython-3.14.0%2B20251014-x86_64-unknown-linux-gnu-install_only.tar.gz"; \
    echo "74d4516a64abc63ae4bcbffb35482879a85b7faa187fcfa47c1ca8f00faebf5f  hostpy.tar.gz" | sha256sum -c -; \
    mkdir -p /opt/python314; \
    tar -xzf hostpy.tar.gz -C /opt/python314 --strip-components=1; \
    rm -f hostpy.tar.gz; \
    /opt/python314/bin/python3 -c "import sys,ssl,lzma,bz2,ctypes,sqlite3; print('HOSTPY', sys.version.split()[0])"

# --- Rust 1.99.0 (pinned) ----------------------------------------------------
RUN set -e; \
    curl -fsSL https://sh.rustup.rs -o /tmp/rustup.sh; \
    sh /tmp/rustup.sh -y --no-modify-path --profile minimal --default-toolchain 1.99.0; \
    rm -f /tmp/rustup.sh; \
    /opt/cargo/bin/rustup target add aarch64-linux-android; \
    /opt/cargo/bin/rustc --version; \
    /opt/cargo/bin/rustup target list --installed

# --- OpenSSL 3.0.18 tree (identical input to the Chaquopy 3.14 runtime) ------
# Downloaded here from the pinned upstream URL rather than COPY-ed from a
# local directory, so the image is reproducible from this file alone.
# Provenance established in M0-008L-B: CPython 3.14.0 Android/android.py
# unpacks this exact artifact to build libcrypto_python.so / libssl_python.so.
#
# NOTE ON CHECKSUMS: the upstream release provides NO checksum
# (CPython's download() is a bare curl -Lf --retry 5). The hash below was
# computed LOCALLY by this probe and is therefore labelled LOCAL, not upstream.
ARG OPENSSL_ARTIFACT_SHA256=2ab2d0caf1d32a77a7ddb84ef92ff5ef518b7b1754178e96649555b510edbade
RUN set -e; \
    cd /tmp; \
    curl -fsSL -o openssl.tar.gz \
      "https://github.com/beeware/cpython-android-source-deps/releases/download/openssl-3.0.18-0/openssl-3.0.18-0-aarch64-linux-android.tar.gz"; \
    echo "${OPENSSL_ARTIFACT_SHA256}  openssl.tar.gz" | sha256sum -c -; \
    mkdir -p /openssl; \
    tar -xzf openssl.tar.gz -C /openssl; \
    rm -f openssl.tar.gz; \
    grep -E 'OPENSSL_VERSION_TEXT' /openssl/include/openssl/opensslv.h; \
    ls -la /openssl/lib/libcrypto_python.so /openssl/lib/libssl_python.so

# --- CPython 3.14.0 source (exact tag, verified hash) -----------------------
COPY Python-3.14.0.tgz /build/Python-3.14.0.tgz
RUN set -e; \
    cd /build; \
    echo "88d2da4eed42fa9a5f42ff58a8bc8988881bd6c547e297e46682c2687638a851  Python-3.14.0.tgz" | sha256sum -c -; \
    mkdir -p /build/cpython-src; \
    tar -xzf Python-3.14.0.tgz -C /build/cpython-src --strip-components=1; \
    rm -f Python-3.14.0.tgz

# --- persistent workspace ----------------------------------------------------
# /build/work is the single writable directory that survives across every
# invocation. This is what M0-008L-E got wrong.
RUN mkdir -p /build/work
WORKDIR /build/work

CMD ["sleep", "infinity"]
