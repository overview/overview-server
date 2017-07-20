#!/bin/sh

# Requires npm to be installed in the user's path.

(cd "$(dirname $0)/../web/test/assets/javascripts/autotest" && npm install)
