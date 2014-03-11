#!/bin/sh

# Before running this script, run setup-integration-tests.sh in this
# same directory to install dependencies. (If integration tests fail, please
# re-run setup-integration-tests.sh before filing a bug, in case the bug has
# to do with dependencies' versions.)

((cd "$(dirname $0)/../test/integration" && npm test))
