# k3s:start

Create and start k3s container.

| Name | User Property | Description | Default |
| -----| ------------- | ----------- | ------- |
| `workdir` | `k3s.workdir` | k3s working directory. This directory is mounted into docker container. | target/k3s |
| `imageRegistry` | `k3s.imageRegistry` | k3s image registry | |
| `imageRepository` | `k3s.imageRepository` | k3s image repository | rancher/k3s |
| `imageTag` | `k3s.imageTag` | k3s image tag | v1.23.4-k3s1 |
| `streamLogs` | `k3s.streamLogs` | Stream logs of `k3s` to maven logger. | false |
| `portKubeApi` | `k3s.portKubeApi` | KubeApi port to expose to host. | 6443 |
| `disableHelmController` | `k3s.disable.helmController` | Disable helm controller. | true |
| `disableLocalStorage` | `k3s.disable.localStorage` | Disable local storage. | true |
| `disableTraefik` | `k3s.disable.traefik` | Disable traefik. | true |
| `nodeTimeout` | `k3s.nodeTimeout` | Timeout in seconds to wait for nodes getting ready. | 120 |
| `podWait` | `k3s.podWait` | Wait for pods getting ready? | false |
| `podTimeout` | `k3s.podTimeout` | Timeout in seconds to wait for pods getting ready. | 300 |
| `skipStart` | `k3s.skipStart` | Skip starting k3s container. | false |
