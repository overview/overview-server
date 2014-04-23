#!/bin/sh

SBT_OPTS="-Xms512M -Xmx2048M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=512M"
SBT_JARFILE="$(dirname $0)/runner/src/main/resources/sbt-launch.jar"
java $SBT_OPTS -jar "$SBT_JARFILE" "$@"
