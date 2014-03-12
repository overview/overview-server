#!/bin/sh

npm install -g grunt-cli coffee-script
(cd "$(dirname $0)/../test/assets/javascripts/autotest" && npm install)
