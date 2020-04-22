#!/usr/bin/env bash
# docker-entrypoint.sh won't run its `_main` when it's sourced from elsewhere
source /usr/local/bin/docker-entrypoint.sh

if test -f /var/lib/postgresql/data/PG_VERSION && test "$(cat /var/lib/postgresql/data/PG_VERSION)" = "9.4"; then
  /pg-upgrade/9.4-to-12.sh
fi

# Continue with whatever docker-entrypoint.sh would do
_main "$@"
