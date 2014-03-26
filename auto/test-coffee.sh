#!/bin/sh

# Before running this script, run ./setup-coffee-tests.sh

(cd "$(dirname $0)/../test/assets/javascripts/autotest" && npm run-script test-continuous)
