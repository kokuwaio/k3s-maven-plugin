# k3s:start

Start k3s container.

| Name | User Property | Description | Default |
| -----| ------------- | ----------- | ------- |
| `streamLogs` | `k3s.streamLogs` | Stream logs of `k3s` to maven logger. | false |
| `nodeTimeout` | `k3s.nodeTimeout` | Timeout in seconds to wait for nodes getting ready. | 120 |
| `podWait` | `k3s.podWait` | Wait for pods getting ready? | false |
| `podTimeout` | `k3s.podTimeout` | Timeout in seconds to wait for pods getting ready. | 300 |
| `skipStart` | `k3s.skipStart` | Skip starting k3s container. | false |
