#!/bin/sh

DIR="$(dirname "$0")"/..

cd "$DIR"
# Make $DIR an absolute path, so we can refer to it later
DIR="$(pwd)"

set -e # any error means the whole thing failed
set -x

DOCKER_COMPOSE="docker-compose --project-name overviewjenkins"
# Failure: https://github.com/docker/compose/issues/4076 -- this affects Jenkins
# So if we run this, we won't see any output:
#exec docker-compose run --rm --no-deps dev "$@"
# Solution: call Docker directly
DOCKER_RUN="docker run --rm -it --network overviewjenkins_default --volume overviewjenkins_database-data:/var/lib/postgresql/data --volume overviewjenkins_search-data:/var/lib/overview/search --volume overviewjenkins_blob-storage-data:/var/lib/overview/blob-storage --volume overviewjenkins_homedir:/root --volume $DIR:/app --publish 127.0.0.1:9000:80 overview-dev:latest"

# Clean everything. Note that we're wiping all data for the "overviewjenkins"
# project, not the default "overviewserver" project that you use in dev mode.
$DOCKER_COMPOSE kill
$DOCKER_COMPOSE down -v

# Launch dependencies early. We use them throughout.
#
# This initializes the volumes and network on which $DOCKER_RUN relies
$DOCKER_COMPOSE up -d database redis

# Compile everything, or abort. Suppress excessive output.
$DOCKER_RUN /app/sbt '; set every logLevel := Level.Warn; common/test:compile; worker/test:compile; web/test:compile; db-evolution-applier/run; test-db-evolution-applier/run'
$DOCKER_RUN /app/build archive.zip

# CoffeeScript tests are the fastest, so run them first
$DOCKER_RUN /app/auto/test-coffee-once.sh || true # Jenkins will pick up test-result XML

# The actual unit tests
$DOCKER_RUN /app/sbt all/test || true # Jenkins will pick up test-result XML

# Run integration tests with the production jarfiles.
#
# We'll extract our production.zip into /tmp/archive/. We'll have
# jars in /tmp/archive/web/*.jar, /tmp/archive/worker/*.jar,
# /tmp/archive/db-evolution-applier/*.jar.
echo 'Launching Overview...' >&2

COMMANDS=<<"EOT"
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
java -cp db-evolution-applier/* \
  com.overviewdocs.db_evolution_applier.Main

# Run worker and leave it running
DATABASE_SERVER_NAME=database \
BLOB_STORAGE_FILE_BASE_DIRECTORY=/var/lib/overview/blob-storage/prod \
OV_SEARCH_DIRECTORY=/var/lib/overview/search/prod \
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
BLOB_STORAGE_FILE_BASE_DIRECTORY=/var/lib/overview/blob-storage/prod \
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
