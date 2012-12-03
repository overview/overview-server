#!/bin/sh
#
# This script runs as root and initializes Overview
#
# It can be run multiple times without negative effects

SUDO_PASSWORD=overview

DIST_ZIP=/home/overview/dist.zip
VM_FILES=/home/overview/vm-files.tar.gz

set -e
set -x

# Stop Overview, if it's running
echo "$SUDO_PASSWORD" | sudo -S killall java || true

# Create user and database. (Ignore failures.)
echo "$SUDO_PASSWORD" | sudo -S -u postgres psql -c "CREATE ROLE overview LOGIN PASSWORD 'overview'" || true
echo "$SUDO_PASSWORD" | sudo -S -u postgres psql -c "CREATE DATABASE overview OWNER overview TEMPLATE template0 ENCODING 'UTF8' LC_COLLATE 'en_US.UTF8' LC_CTYPE 'en_US.UTF8'" || true

# Expand vm-files.tar.gz
echo "$SUDO_PASSWORD" | sudo -S tar zxf $VM_FILES -C / --strip-components=1

# Expand dist.zip
echo "$SUDO_PASSWORD" | sudo -S unzip -o -j $DIST_ZIP -d /opt/overview/lib

# Install init scripts
echo "$SUDO_PASSWORD" | sudo -S update-rc.d overview-worker defaults
echo "$SUDO_PASSWORD" | sudo -S update-rc.d overview-server defaults

# Start Overview
echo "$SUDO_PASSWORD" | sudo -S service overview-worker start
echo "$SUDO_PASSWORD" | sudo -S service overview-server start

# Delete these files
#rm -f $DIST_ZIP $VM_FILES $0
