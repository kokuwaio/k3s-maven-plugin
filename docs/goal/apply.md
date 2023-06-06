# k3s:apply

Runs kubectl to apply manifests.

| Name | User Property | Description | Default |
| -----| ------------- | ----------- | ------- |
| `cacheDir` | `k3s.cacheDir` | Cache directory where to store node informations (mounted to `/var/lib/rancher/k3s/agent`). | `~/.kube/k3s-maven-plugin` |
| `manifests` | `k3s.manifests` | Path where to find manifest files to apply. | src/test/k3s |
| `kustomize` | `k3s.kustomize` | Use kustomize while applying manifest files. | false |
| `timeout` | `k3s.timeout` | Timeout in seconds to wait for resources getting ready. | 300 |
| `skipApply` | `k3s.skipApply` | Skip applying kubectl manifests. | false |
| `debug` | `k3s.debug` | Stream logs of docker and kubectl. | false |
