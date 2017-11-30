FROM postgres:9.4-alpine

COPY overview-setup.sql docker-entrypoint-initdb.d/

# Port 5432: connections from overview-worker, overview-web and overview-db-evolution-applier
