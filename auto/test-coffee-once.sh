#!/bin/sh

DIR="$(dirname "$0")/../web"

# Before running this script, run ./setup-coffee-tests.sh
if command -v xvfb-run >/dev/null 2>&1; then
  (cd "$DIR" && xvfb-run npm test)
else
  (cd "$DIR" && npm test)
fi
