# k3s:kubectl

Runs kubectl commands, applies Kubernetes manifest files per default.

| Name | User Property | Description | Default |
| -----| ------------- | ----------- | ------- |
| `cacheDir` | `k3s.cacheDir` | Cache directory where to store node informations (mounted to `/var/lib/rancher/k3s/agent`). | `~/.kube/k3s-maven-plugin` |
| `manifests` | `k3s.kubectl.manifests` | Path where to find manifest files to apply. | src/test/k3s |
| `command` | `k3s.kubectl.command` | Command to execute. | kubectl apply -R -f . |
| `kubectlTimeout` | `k3s.kubectl.timeout` | Timeout in seconds to wait for kubectl finished. | 30 |
| `podTimeout` | `k3s.kubectl.podTimeout` | Timeout in seconds to wait for pods getting ready. | 300 |
| `skipKubectl` | `k3s.skipKubectl` | Skip plugin. | false |
| `debug` | `k3s.debug` | Stream logs of docker and kubectl. | false |
