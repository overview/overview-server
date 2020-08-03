To set up
---------

1. Run all the scripts in the `eks/` folder, in order, to set up the cluster
1. Run `VERSION=$(git rev-parse HEAD) ./apply` to create deployments
1. Point DNS to the newly-created load balancer

To change cluster configuration
-------------------------------

1. Edit files in this directory
1. `VERSION=$(git rev-parse HEAD) ./apply-file FILE.YML`

To deploy new images
--------------------

TODO give Jenkins a way to trigger deployment.

In the meantime: `VERSION=$(git rev-parse HEAD) ./apply`
