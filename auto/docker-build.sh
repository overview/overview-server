#/bin/bash

cd "$(dirname "$0")"/..

# Support services
docker build --rm -t overview/database docker/database
docker build --rm -t overview/searchindex docker/searchindex

# Create a base with everything built and staged
docker build --rm -t overview/overview-base docker/overview-base

docker build --rm -t overview/db-evolution-applier docker/db-evolution-applier
docker build --rm -t overview/documentset-worker docker/documentset-worker
docker build --rm -t overview/worker docker/worker
docker build --rm -t overview/web docker/web
