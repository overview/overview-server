#!/bin/sh

DIR="$(dirname "$0")"/..

cd "$DIR"
# Make $DIR an absolute path, so we can refer to it later
DIR="$(pwd)"

set -e # any error means the whole thing failed
set -x

DOCKER_COMPOSE="docker-compose --project-name overviewjenkins"
DOCKER_RUN="$DOCKER_COMPOSE run --rm --no-deps dev"

# Clean everything. Note that we're wiping all data for the "overviewjenkins"
# project, not the default "overviewserver" project that you use in dev mode.
$DOCKER_COMPOSE kill
$DOCKER_COMPOSE down -v

# Launch dependencies early. We use them throughout.
$DOCKER_COMPOSE up -d database redis

# Compile everything, or abort. Suppress excessive output.
$DOCKER_RUN /app/sbt '; set every logLevel := Level.Warn; common/test:compile; worker/test:compile; web/test:compile; db-evolution-applier/run; test-db-evolution-applier/run'
$DOCKER_RUN /app/build overview-server.zip

# CoffeeScript tests are the fastest, so run them first
$DOCKER_RUN /app/auto/test-coffee-once.sh || true # Jenkins will pick up test-result XML

# The actual unit tests
$DOCKER_RUN /app/sbt all/test || true # Jenkins will pick up test-result XML

# Run integration tests with the production jarfiles.
#
# We'll extract our production.zip into /tmp/overview-server/. We'll have
# jars in /tmp/overview-server/web/*.jar, /tmp/overview-server/worker/*.jar,
# /tmp/overview-server/db-evolution-applier/*.jar.
echo 'Launching Overview...' >&2

COMMANDS=<<"EOT"
set -e
set -x
cd /tmp
unzip -o -q /app/overview-server.zip
cd overview-server/lib
ln -f $(cat ../db-evolution-applier/classpath.txt) ../db-evolution-applier/
ln -f $(cat ../web/classpath.txt) ../web/
ln -f $(cat ../worker/classpath.txt) ../worker/
cd /tmp/overview-server

# Run db-evolution-applier and wait until it's done
DATABASE_SERVER_NAME=database \
java -cp db-evolution-applier/* \
  com.overviewdocs.db_evolution_applier.Main

# Run worker and leave it running
DATABASE_SERVER_NAME=database \
OV_N_DOCUMENT_CONVERTERS=3 \
java -cp worker/* \
  -Xmx600m \
  com.overviewdocs.Worker \
  &

# Run server and leave it running
#
# we won't run with '-Dconfig.resource=production.conf', because
# that forces SSL and we aren't testing with SSL. (We _could_....)
DATABASE_SERVER_NAME=database \
REDIS_HOST=redis \
OVERVIEW_MULTI_USER=true \
java -cp web/* \
  -Dhttp.port=80 \
  -Dpidfile.path=/dev/null \
  -Xmx600m \
  play.core.server.ProdServerStart /tmp \
  &

wait
EOT
echo $COMMANDS | $DOCKER_RUN bash &

DOCKER_PID="$!"

echo 'Waiting for Overview to respond to Web requests...' >&2
until curl -qs http://localhost:9000 -o /dev/null; do sleep 1; done

echo 'Waiting another 20s for background jobs, so everything is fast when we test...' >&2
sleep 20

"$DIR"/auto/test-integration.sh || true # Jenkins will pick up test-result XML

kill -9 $DOCKER_PID
wait
$DOCKER_COMPOSE kill
$DOCKER_COMPOSE down -v
