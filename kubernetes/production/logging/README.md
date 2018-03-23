# Logging

We log to ElasticSearch.

## Design decisions

Overview's cluster makes these choices:

### No Persistence

To save money, we don't use a persistent volume. We can revise this decision
once it presents a real-world problem.

Furthermore, we only run a single replica.

### Not Much Security

ElasticSearch gives write access within the entire cluster. Later, we can
consider using secrets to lock it down. If attackers have access to any pod they
can access ElasticSearch.

### kubernetes.io/role = "node"

We log from every node: This is a small cluster.

# Deploy

1. `kubectl apply -f fluentd-elasticsearch/`
1. `kubectl proxy`
1. Browse to http://localhost:8001/api/v1/namespaces/kube-system/services/kibana-logging/proxy
1. Set the timestamp field to `@timestamp` save the config

# Browse

1. `kubectl proxy`
1. Browse to http://localhost:8001/api/v1/namespaces/kube-system/services/https:kibana-logging:/proxy

# References

Started at https://github.com/kubernetes/kubernetes/tree/cea4c98508df1855c8ad97222689ba9e5a1fd6ab/cluster/addons/fluentd-elasticsearch
