#!/bin/sh

. "$(dirname "$0")"/ensure-in-docker.sh

(cd /app/web && npm run-script test-continuously)
