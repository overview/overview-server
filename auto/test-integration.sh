#!/bin/sh

# Runs integration tests. There must be an Overview server running at
# http://localhost:9000.
#
# This does not use Docker.

(cd "$(dirname "$0")/../integration-test" && npm install && npm test)
