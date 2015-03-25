#!/bin/sh

DIR=$(dirname "$0")/..

cd "$DIR"

set -e # any error means the whole thing failed
set -x

auto/setup-coffee-tests.sh
auto/setup-integration-tests.sh
deps/redis/build.sh

# Suppress excessive output from Sbt (resolving / downloading). Either it
# works or it doesn't; all errors are the same to us.
#
# test:update downloads test deps, which are always a superset of deps
./sbt '; set every logLevel := Level.Warn; common/test:update; common-test/update; overview-server/test:update; documentset-worker/test:update; worker/test:update; runner/test:update; db-evolution-applier/update; search-index/update; message-broker/update'

# Check for compiler errors
./sbt '; overview-server/test:compile; common/test:compile; worker/test:compile; documentset-worker/test:compile; runner/test:compile' # with no "|| true"

# CoffeeScript tests are the fastest, so we put them first
./auto/test-coffee-once.sh || true # Jenkins will pick up test-result XML

# Unit tests next.
# We need the database to run for the duration of the test commands; --only-servers redis is close enough because it starts fast.
# We test overview-server/test first to run migrations in the test database.
./dev --only-servers redis --sbt '; overview-server/test; common/test; worker/test; documentset-worker/test; runner/test' || true # Jenkins will pick up test-result XML

# The build must succeed
SBT_OPTIONS="-Dsbt.override.build.repos=true -Dsbt.repository.config=./travis/sbt-repositories" ./build overview-server.zip

# Now that we've built, we can run integration tests with the resulting jars
(cd dist && ./run -Doverview-server.props.overview.multi_user=true -Dworker.jvm.Xmx200M= -Ddocumentset-worker.jvm.Xmx200M= -Dsearchindex.jvm.Xmx200M= -Dmessage-broker.jvm.Xmx200M= -Doverview-server.jvm.Xmx200M= --sh ../travis/wait-and-test-integration.sh) || true # Jenkins will pick up test-result XML
