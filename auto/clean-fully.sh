#!/bin/sh

. "$(dirname "$0")"/ensure-in-docker.sh

set -x
rm -rf /root/* /app/web/public/javascripts/bundle
