#!/bin/sh

DIR=`dirname $0`
JSTD_CONFIG="$DIR/jsTestDriver.conf"

java -jar "$DIR/framework/JsTestDriver.jar" --config "$JSTD_CONFIG" --tests all
