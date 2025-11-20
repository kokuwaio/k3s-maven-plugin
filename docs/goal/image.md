# k3s:image

Import images into k3s containerd.

| Name               | User Property          | Description                                                                       | Default |
| ------------------ | ---------------------- | --------------------------------------------------------------------------------- | ------- |
| `ctrImages`        | `k3s.ctrImages`        | Download given images via `ctr image pull` inside k3s container.                  | []      |
| `tarFiles`         | `k3s.tarFiles`         | Import given tar files via `ctr image import` inside k3s container.               | []      |
| `dockerImages`     | `k3s.dockerImages`     | Copy given images from docker deamon via `ctr image import` inside k3s container. | []      |
| `dockerPullAlways` | `k3s.dockerPullAlways` | Always pull docker images or only if not present.                                 | false   |
| `pullTimeout`      | `k3s.pullTimeout`      | Timout for `ctr image pull` or `docker pull` in seconds.                          | 1200    |
| `skipImage`        | `k3s.skipImage`        | Skip image handling.                                                              | false   |
