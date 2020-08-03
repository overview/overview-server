#!/bin/bash

set -e

: "${POSTGRES_PASSWORD:?Please set POSTGRES_PASSWORD}"

# shellcheck disable=SC2046
aws rds create-db-subnet-group \
  --db-subnet-group-name production-overview \
  --db-subnet-group-description production-overview \
  --subnet-ids $(aws ec2 describe-subnets --query 'Subnets[*].SubnetId' --output text)

aws ec2 create-security-group \
  --group-name production-overview-database \
  --description production-overview-database \
  --vpc-id "$(aws ec2 describe-vpcs --query 'Vpcs[0].VpcId' --output text)"

aws rds create-db-cluster \
  --db-cluster-identifier production-overview \
  --db-subnet-group-name production-overview \
  --vpc-security-group-ids "$(aws ec2 describe-security-groups --group-names production-overview-database --output text --query 'SecurityGroups[0].GroupId')" \
  --engine aurora-postgresql \
  --engine-version 11.7 \
  --engine-mode provisioned \
  --master-username postgres \
  --master-user-password "$POSTGRES_PASSWORD" \
  --backup-retention-period 30 \
  --database-name overview \
  --deletion-protection \
  --tags Key=Environment,Value=production

aws rds create-db-instance \
  --db-cluster-identifier production-overview \
  --db-instance-identifier production-overview-instance-1 \
  --db-instance-class db.t3.large \
  --engine aurora-postgresql

aws rds wait db-instance-available \
  --db-instance-identifier production-overview-instance-1
