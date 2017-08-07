#!/bin/sh

DIR="$(dirname "$0")/../web"

# Before running this script, run ./setup-coffee-tests.sh
if command -v xvfb-run >/dev/null 2>&1; then
  (cd "$DIR" && xvfb-run npm run-script test-continuously)
else
  (cd "$DIR" && npm run-script test-continuously)
fi
