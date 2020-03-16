# TODO upgrade Postgres to an upstream-supported version. This will involve
# big edits to overview-local, overview-server and Kubernetes, to pull in data
# from an earlier version.
#
# Versions >9.4.24 need POSTGRES_HOST_AUTH_METHOD=trust env var? Dunno,
# [2020-03-13, adamhooper] for now let's just stick with 9.4.24
FROM postgres:9.4.24-alpine

COPY overview-setup.sql docker-entrypoint-initdb.d/

# Port 5432: connections from overview-worker, overview-web and overview-db-evolution-applier
