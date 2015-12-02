#!/bin/sh

JS_ENGINE=$(which node >/dev/null && echo 'Node' || echo 'Trireme')
SBT_OPTS="-Xms512M -Xmx2048M -Xss1M -XX:+CMSClassUnloadingEnabled -Dsbt.jse.engineType=${JS_ENGINE}"
SBT_JARFILE="$(dirname $0)/auto/sbt-launch.jar"

set -x

java $SBT_OPTS -jar "$SBT_JARFILE" "$@"
