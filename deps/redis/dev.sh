#!/bin/sh
#
# Redis runner, for Mac OS X and Linux.
#
# Usage: `./dev.sh` (or call it from another directory).
#
# It will run with the config in `redis.conf`.

VERSION="2.8.17"

DIR="$(dirname "$0")"
SRCDIR="$DIR/redis-$VERSION"

(cd "$SRCDIR" && make redis-server 2>&1 && ./src/redis-server ../redis.conf)
