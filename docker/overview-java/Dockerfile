# This build depends on "archive.tar.gz" being in the build context.
# Something like https://github.com/moby/moby/issues/2745#issuecomment-319816125:
# tar -c ./* -C $SOME_OTHER_DIRECTORY some_files_from_other_directory | docker build -
FROM alpine:3.9.4

# Need to install NSS manually -- https://bugs.alpinelinux.org/issues/10126
RUN set -x \
      && apk add --update --no-cache \
        openjdk8-jre-base \
        nss \
        ca-certificates

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
