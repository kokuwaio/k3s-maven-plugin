# k3s:start

Start k3s container.

| Name | User Property | Description | Default |
| -----| ------------- | ----------- | ------- |
| `nodeTimeout` | `k3s.nodeTimeout` | Timeout in seconds to wait for nodes getting ready. | 120 |
| `skipStart` | `k3s.skipStart` | Skip starting k3s container. | false |
| `debug` | `k3s.debug` | Stream logs of docker and kubectl. | false |
