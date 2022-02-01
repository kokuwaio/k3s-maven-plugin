# k3s:kustomize

Applies Kubernetes kustomize files.

| Name | User Property | Description | Default |
| -----| ------------- | ----------- | ------- |
| `workdir` | `k3s.workdir` | k3s working directory. This directory is mounted into docker container. | target/k3s |
| `manifests` | `k3s.kubectl.manifests` | Path where to find manifest files to apply. | src/test/k3s |
| `command` | `k3s.kubectl.command` | Command to execute. | kubectl apply -k . |
| `podTimeout` | `k3s.kubectl.podTimeout` | Timeout in seconds to wait for pods getting ready. | 300 |
| `streamLogs` | `k3s.kubectl.streamLogs` | Stream logs of `kubectl` to maven logger. | false |
| `skipKubectl` | `k3s.skipKubectl` | Skip plugin. | false |
