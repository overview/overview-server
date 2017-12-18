#!/bin/sh
# Include this script to make sure you're running things in a new Docker "dev"
# container. For instance, if your script is:
#
#     #!/bin/sh
#     . auto/ensure-in-docker.sh
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
  # Only build the image if it isn't built already. That should be faster than
  # running `docker-compose build` every time.
  [ -n "$(docker image ls -q overviewserver_dev:latest)" ] || docker-compose build
  exec ./auto/docker-run.sh "$0" "$@"
fi
