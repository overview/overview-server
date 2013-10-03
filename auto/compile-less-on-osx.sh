#!/bin/sh

# Speeds up Less compilation on Linux, using lessc from the command line.
#
# Requirements:
# * `sudo apt-get install inotify-tools nodejs`
# * `sudo npm install less`
#
# Usage:
#
# 1. `COMPILE_LESS=false play run`
# 2. `auto/compile-less-on-linux.sh`
# 3. Edit stylesheets as you normally would
#
# What happens:
#
# Play won't try to compile stylesheets. As of Play 2.1.1, Play is several
# times slower than lessc, even though it runs the exact JavaScript and it
# starts warm.

DIR=$(dirname $0)

WATCH="$DIR/../app/assets/stylesheets/"
SRC="$DIR/../app/assets/stylesheets/main.less"
TARGET1="$DIR/../target/scala-2.10/resource_managed/main/public/stylesheets/main.css"
TARGET2DIR="$DIR/../target/scala-2.10/classes/public/stylesheets" # dunno why
TARGET2="$TARGET2DIR/main.css" # dunno why

lessc --verbose $SRC /tmp/lessc.tmp && mv -v /tmp/lessc.tmp $TARGET1 && (mkdir -p $TARGET2DIR ; ln -vf $TARGET1 $TARGET2)
fswatch $WATCH "lessc --verbose $SRC /tmp/lessc.tmp && mv -v /tmp/lessc.tmp $TARGET1 && (mkdir -p $TARGET2DIR ; ln -vf $TARGET1 $TARGET2)"
