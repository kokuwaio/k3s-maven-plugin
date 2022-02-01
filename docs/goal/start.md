# k3s:start

Create and start k3s container.

| Name | User Property | Description | Default |
| -----| ------------- | ----------- | ------- |
| `workdir` | `k3s.workdir` | k3s working directory. This directory is mounted into docker container. | target/k3s |
| `imageRegistry` | `k3s.imageRegistry` | k3s image registry | |
| `imageRepository` | `k3s.imageRepository` | k3s image repository | rancher/k3s |
| `imageTag` | `k3s.imageTag` | k3s image tag | latest |
| `streamLogs` | `k3s.streamLogs` | Stream logs of `k3s` to maven logger. | false |
| `portBindings` | `k3s.portBindings` | Skip starting k3s container. | [] |
| `portKubeApi` | `k3s.portKubeApi` | KubeApi port to expose to host. | 6443 |
| `nodeTimeout` | `k3s.nodeTimeout` | Timeout in seconds to wait for nodes getting ready. | 60 |
| `podTimeout` | `k3s.podTimeout` | Timeout in seconds to wait for pods getting ready. | 300 |
| `skipStart` | `k3s.skipStart` | Skip starting k3s container. | false |
