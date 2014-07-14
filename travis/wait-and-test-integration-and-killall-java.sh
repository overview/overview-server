#!/bin/sh

# Waits a certain amount of time (for things to start up, presumably) and
# then runs the given command. Then runs killall -9 java.
#
# Why? Because this is the easiest way to get a Travis system up and running:
# "./run --sh travis/wait-and-test-integration.sh". A better way would be for the
# runner to poll port 9000 and only run the shell script when the server is up,
# but that would take more work.
#
# Why the violent shutdown? Because Play adds a shutdown hook that seems to
# prevent it from shutting down itself. We don't know the problem, but we sure
# have a solution :).

sleep 30
"$(dirname "$0")"/../auto/test-integration.sh
killall -9 java
