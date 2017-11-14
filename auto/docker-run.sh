#!/bin/sh
# Run the given command in a new "dev" Docker container.
#
# For instance:
#
#    ./auto/docker-run.sh ps ax
#
# ... will create a new Docker container and run "ps ax" on it
#
# A useful command is: "./auto/docker-run.sh bash" -- to inspect a
# pristene container, before any commands (except bash) run on it.

if [ ! -f /this-is-overview-dev-on-docker ]; then
  exec docker-compose run --rm --no-deps dev "$@"
fi
