#!/bin/sh

# Before running this script, follow instructions in
# test/assets/javascripts/autotest/README.md to set up your test environment.

(cd "$(dirname $0)/../test/assets/javascripts/autotest" && grunt test)
