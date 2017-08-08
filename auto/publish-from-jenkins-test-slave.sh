#!/bin/sh

set -e
set -x

BUCKET="s3://overview-builds.overviewdocs.com"
SHA1="$(git rev-parse HEAD)"

aws s3 cp archive.zip "$BUCKET/$SHA1.zip"
