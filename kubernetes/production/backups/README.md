# Backups

We back up with [k8s-snapshots](https://github.com/miracle2k/k8s-snapshots).

## Design decisions

### kube2iam

k8s-snapshots seems unaware of kube2iam: the README recommends running it on
a master. We run it on a worker node instead.

# Deploy

1. Log into AWS (e.g., `aws configure`) and then run `./configure-aws`
1. `kubectl apply -f .`
1. `./patch-persistent-volumes`
1. Browse to AWS console 5min, 1d and 7d afterwards and satisfy yourself that backups exist.
