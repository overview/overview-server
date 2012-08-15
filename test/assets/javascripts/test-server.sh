#!/bin/sh

DIR=`dirname $0`
SOURCE_DIR="$DIR/../../../app/assets/javascripts"
COFFEE_VERSION=`coffee -v`
JSTD_CONFIG="$DIR/jsTestDriver.conf"

echo 'Cleaning up old files...'
rm -rf "$DIR/src-js" "$DIR/test-js"
mkdir -p "$DIR/src-js" "$DIR/test-js"
cp -a "$SOURCE_DIR/vendor" "$DIR/src-js/vendor"

echo 'Watching CoffeeScript sources and tests and compiling as they change...'
coffee -c -o "$DIR/src"-js -w "$SOURCE_DIR" &
COFFEE_PID1=$!
coffee -c -o "$DIR/test-js" -w "$DIR" &
COFFEE_PID2=$!

echo 'Waiting for JavaScript files to appear...'
sleep 2

echo 'Running js-test-driver...'
echo 'Browse to http://localhost:9876 to make your browser help with testing.'
echo 'Press Ctrl-C to stop the server.'
java -jar "$DIR/framework/JsTestDriver.jar" --config "$JSTD_CONFIG" --port 9876

kill $COFFEE_PID1
kill $COFFEE_PID2
