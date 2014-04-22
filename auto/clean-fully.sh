#!/bin/sh

DIR="$(dirname "$0")"/..

set -x
rm -rf "$DIR"/target "$DIR"/.target "$DIR"/*/target "$DIR"/*/.target "$DIR"/project/project
