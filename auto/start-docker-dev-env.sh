#!/bin/sh

set -e

services_are_online() {
  docker run -i \
    --network overviewserver_default \
    --link overview-dev-database \
    --link overview-dev-redis \
    --rm busybox \
    sh -c '(echo | nc overview-dev-database 5432 2>/dev/null) && (echo | nc overview-dev-redis 6379 2>/dev/null)' \
    >/dev/null 2>&1
}

wait_for_database() {
  # Gotta use "sh", not "/bin/sh", or Windows 10 gives error:
  # stat: C:/Program Files/Git/usr/bin/sh: no such file or directory
  docker run -i \
    --network overviewserver_default \
    --link overview-dev-database \
    --rm busybox \
    sh -c 'until $(echo | nc overview-dev-database 5432 2>/dev/null); do sleep 1; done'
}

start_logging_if_not_started() {
  if [ -z "$logs_pid" ]; then
    docker-compose logs -f --tail=0 &
    logs_pid=$!
    trap "kill -9 $logs_pid" EXIT
  fi
}

start_services() {
  docker-compose up -d
  start_logging_if_not_started

  echo "Waiting for database to spin up..."
  wait_for_database

  # Configure Postgres -- overwriting existing config.
  #
  # Two cases to remember:
  # 1. When we run ./dev for the first time, Postgres will run initdb. Initdb
  #    will FAIL if we wrote a config file to the database directory. See
  #    https://github.com/docker-library/postgres/issues/105
  # 2. When we run ./dev for the millionth time, we may have adjusted some server
  #    settings.
  echo "Configuring database and waiting for it to restart..."
  cat "$(dirname "$0")"/../dev-config/pg-setup.sql | \
    docker exec -i overview-dev-database \
      psql -U postgres postgres

  docker-compose restart overview-dev-database
  wait_for_database
}

services_are_online && echo "Services are online" || start_services
