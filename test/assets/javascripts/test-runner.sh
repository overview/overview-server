#!/bin/sh

DIR=`dirname $0`
JSTD_CONFIG="$DIR/jsTestDriver.conf"

java -jar "$DIR/framework/JsTestDriver.jar" \
  --captureConsole \
  --config "$JSTD_CONFIG" \
  --reset \
  --tests all
