#!/bin/sh

. "$(dirname "$0")"/start-docker-dev-env.sh

cleanup() {
  stop_logging_if_started
  kill $(jobs -p)
}

trap cleanup EXIT

start_logging_if_not_started

archive/db-evolution-applier/db-evolution-applier

archive/worker/worker &
archive/web/web -Dpidfile.path=/dev/null &

curl --retry-connrefused --retry 99999 --output /dev/null --silent http://localhost:9000

sleep 5 # ensure worker _and_ web have started

(cd web/test/integration && npm install && npm run test-with-jenkins) || true
