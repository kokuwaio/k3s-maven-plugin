# k3s:kubectl

Runs kubectl commands, applies Kubernetes manifest files per default.

| Name | User Property | Description | Default |
| -----| ------------- | ----------- | ------- |
| `cacheDir` | `k3s.cacheDir` | Cache directory where to store node informations. | `java.io.tmpdir` |
| `manifests` | `k3s.kubectl.manifests` | Path where to find manifest files to apply. | src/test/k3s |
| `command` | `k3s.kubectl.command` | Command to execute. | kubectl apply -f . |
| `kubectlTimeout` | `k3s.kubectl.timeout` | Timeout in seconds to wait for kubectl finished. | 30 |
| `podTimeout` | `k3s.kubectl.podTimeout` | Timeout in seconds to wait for pods getting ready. | 300 |
| `streamLogs` | `k3s.kubectl.streamLogs` | Stream logs of `kubectl` to maven logger. | false |
| `kubectlPath` | `k3s.kubectl.path` | `kubectl` to use on host | |
| `skipKubectl` | `k3s.skipKubectl` | Skip plugin. | false |
