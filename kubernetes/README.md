To set up
---------

1. Set up Jenkins
1. Install [kops](https://github.com/kubernetes/kops/blob/master/docs/install.md) and [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/)
1. Log in to AWS using `aws configure`
1. `aws/create-staging-cluster`

On production, we also followed instructions in `production/` to add logging,
backups and load monitoring.

To change cluster configuration
-------------------------------

1. Edit files in this directory
1. Re-run `./apply`

To deploy new images
--------------------

1. Jenkins should call `./deploy`
