#!/bin/sh
#
# Publishes binaries to S3 and Docker Hub.
#
# Jenkins invokes this when all tests pass.
#
# Docker images are tagged :latest and :[sha1].

set -e
set -x

# Push to S3
BUCKET="s3://overview-builds.overviewdocs.com"
aws s3 cp archive.zip "$BUCKET/$SHA1.zip"

# Push to Docker
"$(dirname "$0")"/../docker/push
