#!/bin/sh

DIR="$(dirname "$0")"/..

set -x
rm -rf "$DIR"/target \
  "$DIR"/.target \
  "$DIR"/*/target \
  "$DIR"/*/.target \
  "$DIR"/upgrade/*/target \
  "$DIR"/project/project \
  "$DIR"/web/public/javascripts/bundle
