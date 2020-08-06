#!/bin/bash

set -e

# https://docs.aws.amazon.com/eks/latest/userguide/alb-ingress.html

subnets=$(aws ec2 describe-subnets --filter Name=tag-key,Values=kubernetes.io/cluster/production-overview --query 'Subnets[*].SubnetArn' --output text)
# shellcheck disable=SC2086
aws resourcegroupstaggingapi tag-resources \
  --resource-arn-list $subnets \
  --tags kubernetes.io/role/elb=1

curl -o /tmp/iam-policy.json https://raw.githubusercontent.com/kubernetes-sigs/aws-alb-ingress-controller/v1.1.8/docs/examples/iam-policy.json
aws iam create-policy \
    --policy-name ALBIngressControllerIAMPolicy \
    --policy-document file:///tmp/iam-policy.json

kubectl apply -f https://raw.githubusercontent.com/kubernetes-sigs/aws-alb-ingress-controller/v1.1.8/docs/examples/rbac-role.yaml

eksctl utils associate-iam-oidc-provider \
    --region us-east-1 \
    --cluster production-overview \
    --approve

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
          "${OIDC_PROVIDER}:sub": "system:serviceaccount:kube-system:alb-ingress-controller"
        }
      }
    }
  ]
}
EOF

aws iam create-role \
  --role-name eks-alb-ingress-controller \
  --description eks-alb-ingress-controller \
  --assume-role-policy-document "$TRUST_RELATIONSHIP"
aws iam attach-role-policy \
  --role-name eks-alb-ingress-controller \
  --policy-arn arn:aws:iam::"$AWS_ACCOUNT_ID":policy/ALBIngressControllerIAMPolicy

kubectl annotate serviceaccount -n kube-system alb-ingress-controller \
  eks.amazonaws.com/role-arn=arn:aws:iam::"$AWS_ACCOUNT_ID":role/eks-alb-ingress-controller

kubectl apply -f https://raw.githubusercontent.com/kubernetes-sigs/aws-alb-ingress-controller/v1.1.8/docs/examples/alb-ingress-controller.yaml

kubectl patch deployment -n kube-system alb-ingress-controller \
  --type json \
  -p '[{"op":"replace","path":"/spec/template/spec/containers/0/args","value":["--ingress-class=alb","--cluster-name=production-overview"]}]'
