#!/bin/sh

# Requires npm to be installed in the user's path.

. "$(dirname "$0")"/ensure-in-docker.sh

(cd /app/web && npm install)
