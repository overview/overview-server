#!/bin/sh
# Include this script to make sure you're running things in a new Docker "dev"
# container. For instance, if your script is:
#
#     #!/bin/sh
#     . auto/in-docker.sh
#     /app/sbt all/test
#
# ... then this will run on the host:
#
#     docker-compose run --no-deps dev /app/sbt all/test
#
# ... which will create a new container and run this:
#
#     /app/sbt all/test

if [ ! -f /this-is-overview-dev-on-docker ]; then
  exec docker-compose run --rm --no-deps dev "$0" "$@"
fi
