#!/bin/sh

DIR=$(dirname "$0")/..

cd "$DIR"

set -e # any error means the whole thing failed
set -x

time auto/setup-coffee-tests.sh
time auto/setup-integration-tests.sh
time deps/redis/build.sh

# Suppress excessive output from Sbt (resolving / downloading). Either it
# works or it doesn't; all errors are the same to us.
#
# We cache with Artifactory. Configure repos here: http://repo-proxy.overviewproject.org.
# Any repo that's in Build.scala, plugins.sbt or default sbt needs to be in
# there for Travis to build. Any repo that _isn't_ should be _gone_, because
# that will make cache misses much faster.
#
# Set the cache expiry times for hits _and_ misses far into the future. Ivy
# generates N-1 misses for every 1 hit, where N is the total number of
# repositories.
time ./sbt -Dsbt.log.noformat=true -Dsbt.override.build.repos=true -Dsbt.repository.config=./travis/sbt-repositories '; set every logLevel := Level.Warn; common/update; common-test/update; overview-server/update; documentset-worker/update; worker/update; runner/update; db-evolution-applier/update; search-index/update; message-broker/update'

# CoffeeScript tests are the fastest, so we put them first
time ./auto/test-coffee-once.sh

# Unit tests next.
# We need the database to run for the duration of the test commands; --only-servers redis is close enough because it starts fast.
# We test overview-server/test first to run migrations in the test database.
time ./dev --only-servers redis --sbt '; overview-server/test; common/test; worker/test; documentset-worker/test; runner/test'

# The build must succeed
SBT_OPTIONS="-Dsbt.override.build.repos=true -Dsbt.repository.config=./travis/sbt-repositories" time ./build overview-server.zip

# Now that we've built, we can run integration tests with the resulting jars
# Suppress excessive output from SauceConnect. Assume it works; there are way too many messages otherwise.
curl -L https://gist.githubusercontent.com/santiycr/5139565/raw/sauce_connect_setup.sh | bash > /dev/null
(cd dist && ./run -Doverview-server.props.overview.multi_user=true -Dworker.jvm.Xmx200M= -Ddocumentset-worker.jvm.Xmx200M= -Dsearchindex.jvm.Xmx200M= -Dmessage-broker.jvm.Xmx200M= -Doverview-server.jvm.Xmx200M= --sh ../travis/wait-and-test-integration.sh)
