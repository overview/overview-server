#!/bin/bash

# https://aws.amazon.com/premiumsupport/knowledge-center/eks-persistent-storage/

AWS_ACCOUNT_ID="$(aws sts get-caller-identity --query "Account" --output text)"

curl -O https://raw.githubusercontent.com/kubernetes-sigs/aws-ebs-csi-driver/v0.4.0/docs/example-iam-policy.json
aws iam create-policy --policy-name Amazon_EBS_CSI_Driver --policy-document file://example-iam-policy.json
aws iam attach-role-policy \
  --policy-arn arn:aws:iam::"$AWS_ACCOUNT_ID":policy/Amazon_EBS_CSI_Driver \
  --role-name production-overview-eksWorkerNodeRole
kubectl apply -k "github.com/kubernetes-sigs/aws-ebs-csi-driver/deploy/kubernetes/overlays/stable/?ref=master"
