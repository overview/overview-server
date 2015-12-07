#!/bin/sh

DIR="$(dirname "$0")"/..

cd "$DIR"

set -e # any error means the whole thing failed
set -x

# Start our dependencies early, so they're sure to be up when we need 'em
#
# The dev servers aren't exactly the same as production, but they're close
# enough. In particular, ElasticSearch is the "dev" one -- so this script
# conflicts with the dev environment!
docker-compose up -d

export DATABASE_PORT=9010
export OVERVIEW_MULTI_USER=true
# All other defaults are good

auto/setup-coffee-tests.sh
auto/setup-integration-tests.sh

# Ensure everything compiles, or abort. Suppress excessive output.
./sbt '; set every logLevel := Level.Warn; common/test:compile; worker/test:compile; overview-server/test:compile; db-evolution-applier/run; test-db-evolution-applier/run'
./build overview-server.zip

# CoffeeScript tests are the fastest, so run them first
./auto/test-coffee-once.sh || true # Jenkins will pick up test-result XML

# The actual unit tests
./sbt all/test || true # Jenkins will pick up test-result XML

# Now that we've built, run integration tests with the resulting jars
unzip -o -q overview-server.zip
overview-server/db-evolution-applier/db-evolution-applier
overview-server/frontend/overview-server &
PIDS="$!"
overview-server/worker/worker &
PIDS="$PIDS $!"

echo "Waiting for server to start..." >&2
until $(echo | nc localhost 9000 2>/dev/null); do sleep 1; done
echo "Waiting an additional 60s for system to calm down..." >&2
sleep 60

(cd "$DIR"/test/integration && npm run-script test-with-jenkins) || true

kill $PIDS
wait
