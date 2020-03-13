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
  DIR="$(realpath "$(dirname "$0")"/..)"

  RUNNING_CONTAINER=$(docker ps -q -f name=overview-server_dev)
  if [ -z "$RUNNING_CONTAINER" ]; then
    # If overview-server_dev is not running, use `docker run`

    # [2020-03-13] support old volume names and new volume names
    CMD="docker run --rm -it --network overview-server_default --volume $DIR:/app"
    for vollink in database-data:/var/lib/postgresql/data  search-data:/var/lib/overview/search blob-storage-data:/var/lib/overview/blob-storage homedir:/root; do
      volname="${vollink%%:*}"
      volpath="${vollink#*:}"
      volfullname="$(docker volume ls -f name="overview-server_$volname|overviewserver_$volname" -q | cut -d' ' -f1)"
      if [ "x$volfullname" = "x" ]; then
        docker volume create $volfullname
        volfullname="overview-server_$volname"
      fi
      CMD="$CMD --volume $volfullname:$volpath"
    done

    # Publish port 9000=>80, if nothing else is running on it
    if [ -z "$(docker ps -q -f publish=80 -f ancestor=overview-server_dev:latest)" ]; then
      echo "Exposing localhost:9000" >&2
      CMD="$CMD --publish 127.0.0.1:9000:80"
    fi

    DOCKER_OPTIONS=
    # Add docker options _before_ the image name
    while [ "x$1" != "x${1#-}" ]; do
      DOCKER_OPTIONS="$DOCKER_OPTIONS $1"
      shift
    done

    CMD="$CMD $DOCKER_OPTIONS overview-server_dev:latest"
  else
    # overviewserver_dev is already running: use `docker exec` within it
    CMD="docker exec -it ${RUNNING_CONTAINER}"
  fi

  exec $CMD "$@"
fi
