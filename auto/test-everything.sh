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

# Build all the images we'll use
docker/build

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

$DOCKER_COMPOSE kill
$DOCKER_COMPOSE down -v

docker/test-integration
