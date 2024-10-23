# k3s:image

Import images into k3s containerd.

| Name | User Property          | Description | Default |
| -----|------------------------| ----------- |---------|
| `ctrImages` | `k3s.ctrImages`        | Download given images via `ctr image pull` inside k3s container. | []      |
| `tarFiles` | `k3s.tarFiles`         | Import given tar files via `ctr image import` inside k3s container. | []      |
| `dockerImages` | `k3s.dockerImages`     | Copy given images from docker deamon via `ctr image import` inside k3s container. | []      |
| `dockerPullAlways` | `k3s.dockerPullAlways` | Always pull docker images or only if not present. | false   |
| `pullTimeout` | `k3s.pullTimeout`      |  Timout for `ctr image pull` or `docker pull` in seconds. | 1200    |
| `copyToContainerTimeout` | `k3s.copyToContainerTimeout` | Timout for saving image to tar in seconds. | 60      |
| `saveImageTimeout` | `k3s.saveImageTimeout` | Timout for saving image to tar in seconds. | 60      | 
| `skipImage` | `k3s.skipImage`        | Skip image handling. | false   |
| `debug` | `k3s.debug`            | Stream logs of docker and kubectl. | false   |
