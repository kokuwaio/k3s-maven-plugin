# k3s:copy

Mojo for copying files to docker container.

| Name         | User Property    | Description                                      | Default |
| ------------ | ---------------- | ------------------------------------------------ | ------- |
| `copySource` | `k3s.copySource` | Source path on host to copy to docker container. |         |
| `copyTarget` | `k3s.copyTarget` | Target path in docker container.                 |         |
| `skipCopy`   | `k3s.skipCopy`   | Skip copying files.                              | false   |
