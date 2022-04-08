# k3s:create

Create k3s container.

| Name | User Property | Description | Default |
| -----| ------------- | ----------- | ------- |
| `cacheDir` | `k3s.cacheDir` | Cache directory where to store node informations. | `java.io.tmpdir` |
| `imageRegistry` | `k3s.imageRegistry` | k3s image registry | |
| `imageRepository` | `k3s.imageRepository` | k3s image repository | rancher/k3s |
| `imageTag` | `k3s.imageTag` | k3s image tag | v1.23.4-k3s1 |
| `portBindings` | `k3s.portBindings` | Skip starting k3s container. | [] |
| `portKubeApi` | `k3s.portKubeApi` | KubeApi port to expose to host. | 6443 |
| `disableHelmController` | `k3s.disableZHelmController` | Disable helm controller. | true |
| `disableLocalStorage` | `k3s.disableLocalStorage` | Disable local storage. | true |
| `disableTraefik` | `k3s.disableTraefik` | Disable traefik. | true |
| `skipStart` | `k3s.skipStart` | Skip starting k3s container. | false |
