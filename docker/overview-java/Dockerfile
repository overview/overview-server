# This build depends on "archive.tar.gz" being in the build context.
# Something like https://github.com/moby/moby/issues/2745#issuecomment-319816125:
# tar -c ./* -C $SOME_OTHER_DIRECTORY some_files_from_other_directory | docker build -
FROM alpine:3.7

# tesseract is only for worker. Since Docker will only
# keep one copy of this image, it actually _saves_ space to include
# tesseract in the web+db-evolution-applier images
#
# runit's `chpst` is used by the worker to limit subprocess sizes.
# TODO make each subprocess a separate Docker container
RUN set -x \
      && apk add --update --no-cache \
        openjdk8-jre-base \
        ca-certificates \
        openssl \
        tesseract-ocr \
        ttf-freefont \
        msttcorefonts-installer \
        runit \
        tini \
      && update-ms-fonts && fc-cache -f \
      && wget -P /usr/share/tessdata \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/osd.traineddata \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/ara.cube.bigrams \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/ara.cube.fold \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/ara.cube.lm \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/ara.cube.nn \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/ara.cube.params \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/ara.cube.size \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/ara.cube.word-freq \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/ara.traineddata \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/cat.traineddata \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/deu.traineddata \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/eng.cube.bigrams \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/eng.cube.fold \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/eng.cube.lm \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/eng.cube.nn \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/eng.cube.params \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/eng.cube.size \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/eng.cube.word-freq \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/eng.tesseract_cube.nn \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/eng.traineddata \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/fra.cube.bigrams \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/fra.cube.fold \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/fra.cube.lm \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/fra.cube.nn \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/fra.cube.params \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/fra.cube.size \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/fra.cube.word-freq \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/fra.tesseract_cube.nn \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/fra.traineddata \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/ita.cube.bigrams \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/ita.cube.fold \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/ita.cube.lm \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/ita.cube.nn \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/ita.cube.params \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/ita.cube.size \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/ita.cube.word-freq \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/ita.tesseract_cube.nn \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/ita.traineddata \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/nld.traineddata \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/nor.traineddata \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/por.traineddata \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/rus.cube.fold \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/rus.cube.lm \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/rus.cube.nn \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/rus.cube.params \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/rus.cube.size \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/rus.cube.word-freq \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/rus.traineddata \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/spa.cube.bigrams \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/spa.cube.fold \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/spa.cube.lm \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/spa.cube.nn \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/spa.cube.params \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/spa.cube.size \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/spa.cube.word-freq \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/spa.traineddata \
        https://github.com/tesseract-ocr/tessdata/raw/3.04.00/swe.traineddata

WORKDIR /opt/overview

# Add _all_ jars to the base image. Why not only the ones we need? Because
# presumably the user of one container will use them all. This saves space in
# the common case since each Jarfile only appears once. In the case of
# separate containers running on separate machines, the size impact isn't too
# large.
#
# This is ADD, not COPY: Docker will extract the tarball.
ADD archive.tar.gz /opt/overview/

# Populate /opt/overview/worker, /opt/overview/web, /opt/overview/db-evolution-applier
#
# We hard-link files in separate directories rather than copy them, so there's
# only one copy on disk of each file. (Symlinks would work, too.)
RUN set -x \
      && mkdir -p worker web db-evolution-applier \
      && cat archive/worker/classpath.txt | xargs -I FILE -t ln archive/lib/FILE worker/ \
      && cat archive/web/classpath.txt | xargs -I FILE -t ln archive/lib/FILE web/ \
      && cat archive/db-evolution-applier/classpath.txt | xargs -I FILE -t ln archive/lib/FILE db-evolution-applier/
