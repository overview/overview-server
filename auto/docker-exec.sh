#!/bin/sh
# Run the given command within an already-running Docker container.
#
# For instance:
#
#     ./auto/docker-exec.sh ps ax
#
# ... will connect to the first running "dev" Docker container and run this:
#
#     ps ax
#
# A useful command is: "./auto/docker-exec.sh bash"

if [ ! -f /this-is-overview-dev-on-docker ]; then
  exec docker-compose exec dev "$0" "$@"
fi
