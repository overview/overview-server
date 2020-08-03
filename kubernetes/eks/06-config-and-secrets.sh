#!/bin/bash

set -e

# POSTGRES_PASSWORD is in kubernetes because where else would we store it? A laptop?
: "${POSTGRES_PASSWORD:?Please set POSTGRES_PASSWORD}"
: "${POSTGRES_MIGRATE_PASSWORD:?Please set POSTGRES_MIGRATE_PASSWORD}"
: "${POSTGRES_NORMAL_PASSWORD:?Please set POSTGRES_NORMAL_PASSWORD}"

kubectl create secret generic postgres-db-user \
  --from-literal=username=postgres \
  --from-literal=password="$POSTGRES_PASSWORD"

kubectl create secret generic overviewmigrate-db-user \
  --from-literal=username=overviewmigrate \
  --from-literal=password="$POSTGRES_MIGRATE_PASSWORD"

kubectl create secret generic overview-db-user \
  --from-literal=username=overview \
  --from-literal=password="$POSTGRES_NORMAL_PASSWORD"

DATABASE_ENDPOINT=$(aws rds describe-db-clusters --db-cluster-identifier production-overview --query DBClusters[0].Endpoint --output text)
kubectl create configmap database-config \
  --from-literal=DATABASE_SERVER_NAME="$DATABASE_ENDPOINT" \
  --from-literal=DATABASE_PORT=5432 \
  --from-literal=DATABASE_NAME=overview \
  --from-literal=DATABASE_SSL=true \
  --from-literal=DATABASE_SSL_FACTORY=org.postgresql.ssl.jdbc4.LibPQFactory

# TODO document play.yaml, smtp.yaml and mailchimp.yaml
