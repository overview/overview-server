#!/bin/sh

DIR="$(dirname "$0")"/..

cd "$DIR"
# Make $DIR an absolute path, so we can refer to it later
DIR="$(pwd)"

set -e # any error means the whole thing failed
set -x

PROJECT=overviewjenkins

DOCKER_COMPOSE="docker-compose --project-name $PROJECT"
# Failure: https://github.com/docker/compose/issues/4076 -- this affects Jenkins
# So if we run this, we won't see any output:
#exec docker-compose run --rm --no-deps dev "$@"
# Solution: call Docker directly
DOCKER_RUN="docker run --rm -i --network ${PROJECT}_default --volume ${PROJECT}_database-data:/var/lib/postgresql/data --volume ${PROJECT}_search-data:/var/lib/overview/search --volume ${PROJECT}_blob-storage-data:/var/lib/overview/blob-storage --volume ${PROJECT}_homedir:/root --volume $DIR:/app"

# Clean everything. Note that we're wiping all data for the "overviewjenkins"
# project, not the default "overviewserver" project that you use in dev mode.
$DOCKER_COMPOSE kill
still_running="$(docker ps -q --filter network="${PROJECT}_default")"
[ -z "$still_running" ] || docker rm -f -v $still_running
$DOCKER_COMPOSE down -v --remove-orphans

# Launch dependencies early. We use them throughout.
#
# This initializes the volumes and network on which $DOCKER_RUN relies
$DOCKER_COMPOSE up -d database redis

# Compile everything, or abort. Suppress excessive output.
$DOCKER_RUN overview-dev /app/sbt '; set every logLevel := Level.Warn; common/test:compile; worker/test:compile; web/test:compile; db-evolution-applier/run; test-db-evolution-applier/run'
$DOCKER_RUN overview-dev /app/build archive.zip

# CoffeeScript tests are the fastest, so run them first
$DOCKER_RUN overview-dev sh -c '(cd /app && ./auto/test-coffee-once.sh) || true' # Jenkins will pick up test-result XML

# Now run sbt unit tests. They'll output in the build directory; move those files
# into /app so the host (Jenkins) can see them.
COMMANDS=$(cat <<"EOT"
set -x
set -e
/app/sbt all/test || true

# Save unit-test results where Jenkins can see them. Error if none were
# generated.
rm -rf /app/unit-test-results
mkdir -p /app/unit-test-results/{common,web,worker,js}
for project in common web worker; do
  cp /root/overview-build/$project/test-reports/* /app/unit-test-results/$project/
done
EOT
)
echo "$COMMANDS" | $DOCKER_RUN overview-dev bash # Jenkins will pick up test-result XML

# Run integration tests with the production jarfiles.
#
# We'll extract our production.zip into /tmp/archive/. We'll have
# jars in /tmp/archive/web/*.jar, /tmp/archive/worker/*.jar,
# /tmp/archive/db-evolution-applier/*.jar.
echo 'Launching Overview...' >&2

COMMANDS=$(cat <<"EOT"
set -e
set -x
cd /tmp
unzip -o -q /app/archive.zip
cd archive/lib
ln -f $(cat ../db-evolution-applier/classpath.txt) ../db-evolution-applier/
ln -f $(cat ../web/classpath.txt) ../web/
ln -f $(cat ../worker/classpath.txt) ../worker/
cd /tmp/archive

# Run db-evolution-applier and wait until it's done
DATABASE_SERVER_NAME=database \
java -cp 'db-evolution-applier/*' \
  com.overviewdocs.db_evolution_applier.Main

# Run worker and leave it running
DATABASE_SERVER_NAME=database \
BLOB_STORAGE_FILE_BASE_DIRECTORY=/var/lib/overview/blob-storage/prod \
OV_SEARCH_DIRECTORY=/var/lib/overview/search/prod \
OV_N_DOCUMENT_CONVERTERS=3 \
java -cp 'worker/*' \
  -Xmx600m \
  com.overviewdocs.Worker \
  &
PIDS="$!"

# Run server and leave it running
#
# we won't run with '-Dconfig.resource=production.conf', because
# that forces SSL and we aren't testing with SSL. (We _could_....)
DATABASE_SERVER_NAME=database \
BLOB_STORAGE_FILE_BASE_DIRECTORY=/var/lib/overview/blob-storage/prod \
REDIS_HOST=redis \
OVERVIEW_MULTI_USER=true \
java -cp 'web/*' \
  -Dhttp.port=80 \
  -Dpidfile.path=/dev/null \
  -Xmx600m \
  play.core.server.ProdServerStart /tmp \
  &
PIDS="$PIDS $!"

# Now wait until SIGTERM or until a service dies -- whichever comes first.
# "wait" will return when a child dies or when the trap is fired.
trap 'echo "Received SIGTERM. Killing children and cleaning up...." >&2' TERM
wait -n $PIDS || true # Wait for SIGTERM or for a child to die. Do not trigger "set -e"
trap - TERM # We're done with the trap

echo 'Killing Overview services...' >&2
kill -9 $PIDS
wait $PIDS || true # exit successfully
EOT
)
echo "$COMMANDS" | $DOCKER_RUN --name dev overview-dev bash &

DOCKER_PID="$!"

echo 'Waiting for Overview to respond to Web requests...' >&2
$DOCKER_RUN overview-dev bash -c 'until curl -qs http://dev -o /dev/null; do sleep 1; done'

(PROJECT="$PROJECT" integration-test/run) || true # Jenkins will pick up the test-result XML

kill $DOCKER_PID
wait $DOCKER_PID
$DOCKER_COMPOSE kill
$DOCKER_COMPOSE down -v
