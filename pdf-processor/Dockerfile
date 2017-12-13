FROM buildpack-deps:stretch-curl

# We're going to try to make these build images as cache-able as possible.
# That means building PDFium as early as possible.
#
# Furthermore, we use several RUN commands, so interim steps can be cached.

# 1. Install compiler
RUN set -x \
      && apt-get -q -y update \
      && apt-get -q -y install git build-essential python

# 2. Install depot_tools
env DEPOT_TOOLS_VERSION=e8856eea42cf284071511107e818ce9980b30636
RUN set -x \
      && cd /usr/src && git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git \
      && cd /usr/src/depot_tools && git checkout $DEPOT_TOOLS_VERSION

# 3. Download pdfium
env PDFIUM_VERSION=92c8b1a07b01f428ec3f2b6d04acc109bfdeb3da
RUN set -x \
      && mkdir -p /usr/src/pdfium_repo \
      && cd /usr/src/pdfium_repo \
      && /usr/src/depot_tools/gclient root \
      && /usr/src/depot_tools/gclient config --spec 'solutions = [ { "url": "https://pdfium.googlesource.com/pdfium.git", "managed": False, "name": "pdfium", }, ]' \
      && PATH="/usr/src/depot_tools:$PATH" /usr/src/depot_tools/gclient sync --no-history --revision=$PDFIUM_VERSION

# 4. Install the rest of our build environment.
RUN set -x \
      && echo "deb [arch=amd64] http://storage.googleapis.com/bazel-apt stable jdk1.8" | tee /etc/apt/sources.list.d/bazel.list \
      && curl https://bazel.build/bazel-release.pub.gpg | apt-key add - \
      && apt-get -q -y update \
      && apt-get -q -y install libboost-dev bazel pkg-config ninja-build clang libc++-dev

# 5. Compile pdfium
#
# PDFium is compiled with clang++ and libc++.
RUN set -x \
      && cd /usr/src/pdfium_repo/pdfium \
      && /usr/src/depot_tools/gn gen out/Release --args="use_goma=false is_debug=false pdf_use_skia=false pdf_use_skia_paths=false pdf_enable_xfa=false pdf_enable_v8=false pdf_is_standalone=true is_component_build=false clang_use_chrome_plugins=false symbol_level=0 pdf_is_complete_lib=true" \
      && ninja -C out/Release pdfium \
      && mkdir -p /usr/include/pdfium \
      && cp -a out/Release/obj/libpdfium.a /usr/lib/libpdfium.a \
      && install -t /usr/include/pdfium/ public/*.h

# Presto! A build environment with PDFium and Boost.
WORKDIR /src
VOLUME /src

COPY . /src/
