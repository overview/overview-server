#!/bin/sh

pushd "$(dirname "$0")/../test/assets/javascripts/autotest"

# Before running this script, run ./setup-coffee-tests.sh
if command -v xvfb-run >/dev/null 2>&1; then
  xvfb-run npm test
else
  npm test
fi
