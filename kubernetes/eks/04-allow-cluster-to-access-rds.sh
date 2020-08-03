#!/bin/bash

set -e

: "${POSTGRES_MIGRATE_PASSWORD:?Please set POSTGRES_MIGRATE_PASSWORD}"
: "${POSTGRES_NORMAL_PASSWORD:?Please set POSTGRES_NORMAL_PASSWORD}"

DATABASE_ENDPOINT=$(aws rds describe-db-clusters --db-cluster-identifier production-overview --query DBClusters[0].Endpoint --output text)
WORKER_NODE_SG=$(aws ec2 describe-security-groups --filter Name=tag:aws:eks:cluster-name,Values=production-overview --query 'SecurityGroups[0].GroupId' --output text)
aws ec2 authorize-security-group-ingress \
  --group-name production-overview-database \
  --source-group "$WORKER_NODE_SG" \
  --protocol tcp \
  --port 5432

kubectl run \
  -i \
  --restart=Never \
  --rm \
  --env PGPASSWORD="$POSTGRES_PASSWORD" \
  --image postgres \
  psql \
  -- psql -h "$DATABASE_ENDPOINT" -U postgres overview -c "CREATE USER overviewmigrate WITH PASSWORD '$POSTGRES_MIGRATE_PASSWORD'; GRANT CONNECT ON DATABASE overview TO overviewmigrate; GRANT ALL PRIVILEGES ON SCHEMA public TO overviewmigrate; GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO overviewmigrate; GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO overviewmigrate; ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO overviewmigrate; ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO overviewmigrate; CREATE USER overview WITH PASSWORD '$POSTGRES_NORMAL_PASSWORD'; GRANT CONNECT ON DATABASE overview TO overview; GRANT USAGE ON SCHEMA public TO overview; GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO overview; GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO overview; ALTER DEFAULT PRIVILEGES FOR ROLE overviewmigrate IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO overview; ALTER DEFAULT PRIVILEGES FOR ROLE overviewmigrate IN SCHEMA public GRANT USAGE ON SEQUENCES TO overview;"
