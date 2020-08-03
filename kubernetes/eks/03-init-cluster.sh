#!/bin/bash

set -e

aws ec2 create-security-group \
  --group-name production-overview-eks \
  --description production-overview-eks \
  --vpc-id "$(aws ec2 describe-vpcs --query 'Vpcs[0].VpcId' --output text)"

aws iam create-role \
  --role-name production-overview-eksClusterRole \
  --tags Key=Environment,Value=production \
  --assume-role-policy-document='{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"eks.amazonaws.com"},"Action": "sts:AssumeRole"}]}'
aws iam attach-role-policy \
  --role-name production-overview-eksClusterRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonEKSClusterPolicy

aws iam create-role \
  --role-name production-overview-eksWorkerNodeRole \
  --tags Key=Environment,Value=production \
  --assume-role-policy-document='{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ec2.amazonaws.com"},"Action": "sts:AssumeRole"}]}'
aws iam attach-role-policy \
  --role-name production-overview-eksWorkerNodeRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy
aws iam attach-role-policy \
  --role-name production-overview-eksWorkerNodeRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy
aws iam attach-role-policy \
  --role-name production-overview-eksWorkerNodeRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly

# Cluster omits some subnets because on 2020-07-31 their availability
# zones didn't have capacity.
aws eks create-cluster \
  --region us-east-1 \
  --name production-overview \
  --kubernetes-version 1.17 \
  --scaling-config minSize=1,maxSize=4,desiredSize=3 \
  --role-arn "$(aws iam get-role --role-name production-overview-eksClusterRole --query 'Role.Arn' --output text)"  \
  --resources-vpc-config "subnetIds=$(aws ec2 describe-subnets --filters Name=availabilityZone,Values=us-east-1a,us-east-1b,us-east-1c --query 'Subnets[*].SubnetId' --output text | sed -e 's/\t/,/g'),securityGroupIds=$(aws ec2 describe-security-groups --group-names production-overview-eks --output text --query 'SecurityGroups[0].GroupId')"

aws eks wait cluster-active \
  --name production-overview

# nodegroup must auto-scale in exactly one AZ -- hence one subnet. Picked quasi-randomly.
# shellcheck disable=SC2046
aws eks create-nodegroup \
  --cluster-name production-overview \
  --nodegroup-name production-overview-ng-2 \
  --node-role "$(aws iam get-role --role-name production-overview-eksWorkerNodeRole --query 'Role.Arn' --output text)"  \
  --disk-size 60 \
  --subnets $(aws ec2 describe-subnets --filter Name=tag-key,Values=kubernetes.io/cluster/production-overview --query 'Subnets[*].SubnetId' --output text | cut -f3) \
  --instance-types m5.large \
  --tags Environment=production

aws eks wait nodegroup-active \
  --cluster-name production-overview \
  --nodegroup-name production-overview-ng-1

# Update client kubectl
aws eks --region us-east-1 update-kubeconfig --name production-overview
