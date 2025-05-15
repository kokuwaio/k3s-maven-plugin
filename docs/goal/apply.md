# k3s:apply

Runs kubectl to apply manifests.

| Name | User Property | Description | Default |
| -----| ------------- | ----------- | ------- |
| `manifests` | `k3s.manifests` | Path where to find manifest files to apply. This files are copied to docker container. | src/test/k3s |
| `subdir` | `k3s.subdir` | Subdir of **manifests** to execute. | `null` |
| `timeout` | `k3s.timeout` | Timeout in seconds to wait for resources getting ready. | 300 |
| `skipApply` | `k3s.skipApply` | Skip applying kubectl manifests. | false |
| `debug` | `k3s.debug` | Stream logs of docker and kubectl. | false |
