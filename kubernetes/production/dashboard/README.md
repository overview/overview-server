# Dashboard

Our dashboard shows resources allocated on production

## Design decisions

### Standalone Heapster

**Heapster** (for k8s 1.8) collects statistics on resource usage. It exposes
that info to the dashboard.

"Standalone" Heapster integrates with the dashboard and with the autoscaler,
and that's it. The other option is "Heapster+InfluxDB+Grafana", which is more
stuff.

# Deploy

```
kubectl apply -f https://raw.githubusercontent.com/kubernetes/dashboard/master/aio/deploy/recommended/kubernetes-dashboard.yaml
kubectl apply -f kubernetes-dashboard-rbac.yml
for piece in aggregated-metrics-reader auth-delegator auth-reader metrics-apiservice metrics-server-deployment metrics-server-service resource-reader; do \
    kubectl apply -f https://raw.githubusercontent.com/kubernetes-incubator/metrics-server/9b847a8a60261c51f1b981ef37ff70b3c3b13014/deploy/1.8%2B/$piece.yaml
done
```

Then follow instructions at
https://github.com/kubernetes/dashboard/wiki/Creating-sample-user to create
a user and bearer token. To read the token:

```
kubectl apply -f dashboard-admin-user.yml
kubectl apply -f dashboard-admin-user-cluster-role-binding.yml
```

# Browse

1. `kubectl proxy`
1. Browse to http://localhost:8001/api/v1/namespaces/kube-system/services/https:kubernetes-dashboard:/proxy/
1. Enter the token from: `kubectl -n kube-system describe secret $(kubectl -n kube-system get secret | grep admin-user | awk '{print $1}')`

# References

* https://github.com/kubernetes/kops/blob/master/docs/addons.md
