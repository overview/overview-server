# TODO upgrade Postgres to an upstream-supported version. This will involve
# big edits to overview-local, overview-server and Kubernetes, to pull in data
# from an earlier version.
FROM postgres:12.2-alpine

# https://github.com/docker-library/postgres/issues/201
ENV PGDATA="/var/lib/postgresql/data/12"

COPY docker-entrypoint-with-pg-upgrade.sh postgresql.conf pg_hba.conf /
ENTRYPOINT [ "/docker-entrypoint-with-pg-upgrade.sh" ]
CMD [ "postgres", "-c", "config_file=/postgresql.conf", "-c", "hba_file=/pg_hba.conf" ]
COPY /pg-upgrade/ /pg-upgrade/
COPY overview-setup.sh docker-entrypoint-initdb.d/

# Port 5432: connections from overview-worker, overview-web and overview-db-evolution-applier
