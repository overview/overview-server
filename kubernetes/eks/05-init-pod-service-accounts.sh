#!/bin/bash

set -e

AWS_ACCOUNT_ID="$(aws sts get-caller-identity --query "Account" --output text)"
OIDC_PROVIDER="$(aws eks describe-cluster --name production-overview --query "cluster.identity.oidc.issuer" --output text | sed -e "s/^https:\/\///")"

read -r -d '' TRUST_RELATIONSHIP <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::${AWS_ACCOUNT_ID}:oidc-provider/${OIDC_PROVIDER}"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "${OIDC_PROVIDER}:sub": "system:serviceaccount:default:web"
        }
      }
    }
  ]
}
EOF
aws iam create-policy \
  --policy-name production-overview-web \
  --policy-document file://policy-production-overview-r-s3.json

read -r -d '' TRUST_RELATIONSHIP <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::${AWS_ACCOUNT_ID}:oidc-provider/${OIDC_PROVIDER}"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "${OIDC_PROVIDER}:sub": "system:serviceaccount:default:worker"
        }
      }
    }
  ]
}
EOF
aws iam create-policy \
  --policy-name production-overview-worker \
  --policy-document file://policy-production-overview-rw-s3.json

aws iam create-role \
  --role-name production-overview-web \
  --description production-overview-web \
  --assume-role-policy-document "$TRUST_RELATIONSHIP"
aws iam attach-role-policy \
  --role-name production-overview-web \
  --policy-arn arn:aws:iam::"$AWS_ACCOUNT_ID":policy/production-overview-web

aws iam create-role \
  --role-name production-overview-worker \
  --description production-overview-worker \
  --assume-role-policy-document "$TRUST_RELATIONSHIP"
aws iam attach-role-policy \
  --role-name production-overview-worker \
  --policy-arn arn:aws:iam::"$AWS_ACCOUNT_ID":policy/production-overview-worker

kubectl apply -f <(cat <<EOT
apiVersion: v1
kind: ServiceAccount
metadata:
  name: web
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::$AWS_ACCOUNT_ID:role/production-overview-web
EOT
)

kubectl apply -f <(cat <<EOT
apiVersion: v1
kind: ServiceAccount
metadata:
  name: worker
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::$AWS_ACCOUNT_ID:role/production-overview-worker
EOT
)
