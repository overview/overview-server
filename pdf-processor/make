#!/bin/sh
#
# Runs GNU 'make' in an existing or new Docker container.
#
# Usage:
#
# `./make clean` -- delete build files
# `./make split-pdf-and-extract-text` -- build binary

exec "$(dirname "$0")"/in-docker make "$@"
