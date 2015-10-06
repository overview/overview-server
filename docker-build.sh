#/bin/bash

cd "$(dirname "$0")"


# Support services
docker build -t overview/database docker/database

# Create a base with everything built and staged
docker build -t overview/overview-base  docker/overview-base

# Overview services
docker build -t overview/db-evolution-applier docker/db-evolution-applier
docker build -t overview/documentset-worker docker/documentset-worker
docker build -t overview/worker docker/worker

docker build -t overview/web docker/web
