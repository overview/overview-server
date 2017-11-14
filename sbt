#!/bin/sh

DIR="$(dirname "$0")"

. "$DIR"/auto/ensure-in-docker.sh

java \
  -Xmx2048m \
  -Dsbt.task.timing=true \
  -Dsbt.jse.engineType=Node \
  -jar "$DIR"/auto/sbt-launch.jar \
  "$@"
