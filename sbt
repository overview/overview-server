#!/bin/sh

if which node; then
  JS_ENGINE="Node"
else
  JS_ENGINE="Trireme"
fi

SBT_OPTS="-Xms512M -Xmx2048M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=512M -Dsbt.jse.engineType=${JS_ENGINE}"
SBT_JARFILE="$(dirname $0)/runner/src/main/resources/sbt-launch.jar"

set -x

java $SBT_OPTS -jar "$SBT_JARFILE" "$@"
