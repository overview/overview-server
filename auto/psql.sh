#!/bin/sh
#
# Connects to the Overview database.
#
# Usage:
#
#   auto/psql.sh overview-dev

docker exec -it overview-dev-database psql -U overview "$@"
