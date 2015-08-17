#/bin/bash

cd "$(dirname "$0")"


# Images built in their own directory, with no source in context
docker build -t overview/database docker/database
docker build -t overview/message-broker docker/message-broker


# Create a base with everything built and staged
docker build -t overview/overview-base -f docker/overview-base/Dockerfile .

# Overview services
docker build -t overview/db-evolution-applier docker/db-evolution-applier
docker build -t overview/documentset-worker docker/documentset-worker
docker build -t overview/worker docker/worker

docker build -t overview/web docker/web
