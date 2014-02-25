#!/bin/sh
#
# Connects to the Overview database.
#
# Usage:
#
#   auto/psql.sh overview-dev

psql -h localhost -p 9010 -U overview "$*"
