# k3s:apply

Runs kubectl to apply manifests.

| Name | User Property | Description | Default |
| -----| ------------- | ----------- | ------- |
| `manifests` | `k3s.manifests` | Path where to find manifest files to apply. | src/test/k3s |
| `kustomize` | `k3s.kustomize` | Use kustomize while applying manifest files. | false |
| `timeout` | `k3s.timeout` | Timeout in seconds to wait for resources getting ready. | 300 |
| `skipApply` | `k3s.skipApply` | Skip applying kubectl manifests. | false |
| `debug` | `k3s.debug` | Stream logs of docker and kubectl. | false |
