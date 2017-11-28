#!/bin/sh

DIR="$(dirname "$0")"
. "$DIR"/auto/ensure-in-docker.sh

exec java \
  -Xmx2048m \
  -Dsbt.task.timing=true \
  -Dsbt.jse.engineType=Node \
  -jar /app/auto/sbt-launch.jar \
  "$@"
