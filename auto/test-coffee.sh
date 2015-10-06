#!/bin/sh

# Before running this script, run ./setup-coffee-tests.sh
if command -v xvfb-run >/dev/null 2>&1; then
  xvfb-run npm run-script test-continuously
else
  npm test
fi
