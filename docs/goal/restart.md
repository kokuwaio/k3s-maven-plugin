# k3s:restart

Restart selected resources. Usefull for local development and restarting services after new image was build.

| Name | User Property | Description | Default |
| -----| ------------- | ----------- | ------- |
| `resources` | `k3s.resources` | Deployments to restart. Pattern: `deployment/my-namespace/my-deployment`. | [] |
| `timeout` | `k3s.timeout` | Timeout in seconds to wait for resources getting ready. | 300 |
| `skipRestart` | `k3s.skipImage` | Skip image handling. | false |
| `debug` | `k3s.debug` | Stream logs of docker and kubectl. | false |
| `taskTimeout` | `k3s.taskTimeout` | Default timeout for docker tasks in seconds. | 30 |
