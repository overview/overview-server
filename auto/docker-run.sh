#!/bin/sh
# Run the given command in a new "dev" Docker container.
#
# For instance:
#
#    ./auto/docker-run.sh ps ax
#
# ... will create a new Docker container and run "ps ax" on it
#
# You can pass extra arguments to docker-run.sh:
#
#    ./auto/docker-run.sh --publish 127.0.0.1:9000:80 ./sbt run
#
# A useful command is: "./auto/docker-run.sh bash" -- to inspect a
# pristene container, before any commands (except bash) run on it.

if [ ! -f /this-is-overview-dev-on-docker ]; then
  # Failure: https://github.com/docker/compose/issues/4076 -- this affects Jenkins
  # So if we run this, we won't see any output:
  #exec docker-compose run --rm --no-deps dev "$@"
  # Solution: call Docker directly
  DIR="$(realpath "$(dirname "$0")"/..)"
  CMD="docker run --rm -it --network overviewserver_default --volume overviewserver_database-data:/var/lib/postgresql/data --volume overviewserver_search-data:/var/lib/overview/search --volume overviewserver_blob-storage-data:/var/lib/overview/blob-storage --volume overviewserver_homedir:/root --volume $DIR:/app"

  # Publish port 9000=>80, if nothing else is running on it
  if [ -z "$(docker ps -q -f publish=80 -f ancestor=overviewserver_dev:latest)" ]; then
    echo "Exposing localhost:9000" >&2
    CMD="$CMD --publish 127.0.0.1:9000:80"
  fi

  DOCKER_OPTIONS=
  # Add docker options _before_ the image name
  while [ "x$1" != "x${1#-}" ]; do
    DOCKER_OPTIONS="$DOCKER_OPTIONS $1"
    shift
  done
  exec $CMD $DOCKER_OPTIONS overviewserver_dev:latest "$@"
fi
