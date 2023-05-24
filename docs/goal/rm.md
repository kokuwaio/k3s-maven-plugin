# k3s:rm

Stop and remove k3s container.

| Name | User Property | Description | Default |
| -----| ------------- | ----------- | ------- |
| `cacheDir` | `k3s.cacheDir` | Cache directory where to store node informations (mounted to `/var/lib/rancher/k3s/agent`). | `~/.kube/k3s-maven-plugin` |
| `includeCache` | `k3s.includeCache` | Include cache directory with downloaded images. | false |
| `skipRm` | `k3s.skipRm` | Skip removing k3s container. | false |
| `debug` | `k3s.debug` | Stream logs of docker and kubectl. | false |
