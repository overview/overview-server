#!/bin/sh
#
# Use `./dev.sh` instead. This is more handy as a component of other tools.

VERSION="2.8.17"

DIR="$(dirname "$0")"
SRCDIR="$DIR/redis-$VERSION"

(cd "$SRCDIR"/deps/jemalloc && ./configure && make) # Will this work in Travis?
(cd "$SRCDIR" && bash -c "make redis-server")
