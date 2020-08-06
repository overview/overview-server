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

`kubectl apply -f jenkins-deploy-rbac.yml` to build a service account for
Jenkins to deploy with.

This will create a secret token for Jenkins. Get it:

```
JENKINS_TOKEN_NAME=$(kubectl get sa jenkins-ci -o go-template --template='{{range .secrets}}{{.name}}{{"\n"}}{{end}}') \
  kubectl get secrets $JENKINS_TOKEN_NAME -o go-template --template '{{index .data "token"}}' \
  | base64 -d \
  && echo
```

Add to jenkins using instructions at
https://github.com/overview/overview-server/wiki/How-we-set-up-continuous-integration-on-Jenkins
(Ref: https://plugins.jenkins.io/kubernetes-cli/)
