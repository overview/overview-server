#!/bin/sh
#
# Connects to the Overview database.
#
# Usage:
#
#   auto/psql.sh overview-dev

DATABASE=${1:-overview-dev}

docker-compose exec database psql -U overview "$DATABASE"
