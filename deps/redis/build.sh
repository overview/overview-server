#!/bin/sh
#
# Use `./dev.sh` instead. This is handy because Travis fails when building jemalloc

VERSION="2.8.17"

DIR="$(dirname "$0")"
SRCDIR="$DIR/redis-$VERSION"

(cd "$SRCDIR" && bash -c "make redis-server MALLOC=libc")
