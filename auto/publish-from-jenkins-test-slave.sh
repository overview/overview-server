#!/bin/sh

set -e
set -x

md5sum archive.zip | cut -b1-32 | tr -d '\n' > archive.md5sum

BUCKET="s3://overview-builds.overviewdocs.com"
SHA1="$(git rev-parse HEAD)"

md5sum archive.zip | cut -b1-32 | tr -d '\n' | aws s3 cp - "$BUCKET/$SHA1.md5sum"
aws s3 cp archive.zip "$BUCKET/$SHA1.zip"
