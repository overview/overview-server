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

1. `kubectl apply -f https://raw.githubusercontent.com/kubernetes/kops/79d5f793e74567e196ad1cced142afdd1e793b3f/addons/kubernetes-dashboard/v1.8.1.yaml`
1. `kubectl apply -f kubernetes-dashboard-rbac.yml`
1. `kubectl apply -f https://raw.githubusercontent.com/kubernetes/kops/79d5f793e74567e196ad1cced142afdd1e793b3f/addons/monitoring-standalone/v1.7.0.yaml`

# Browse

1. `kubectl proxy`
1. Browse to http://localhost:8001/api/v1/namespaces/kube-system/services/https:kubernetes-dashboard:/proxy/

# Referneces

* https://github.com/kubernetes/kops/blob/master/docs/addons.md
