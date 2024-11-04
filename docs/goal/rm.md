# k3s:rm

Stop and remove k3s container.

| Name | User Property | Description | Default |
| -----| ------------- | ----------- | ------- |
| `includeCache` | `k3s.includeCache` | Include cache directory with downloaded images. | false |
| `skipRm` | `k3s.skipRm` | Skip removing k3s container. | false |
| `debug` | `k3s.debug` | Stream logs of docker and kubectl. | false |
| `taskTimeout` | `k3s.taskTimeout` | Default timeout for docker tasks in seconds. | 30 |
