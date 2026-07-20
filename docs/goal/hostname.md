# k3s:hostname

Determine docker daemon hostname and set into project.

| Name               | User Property          | Description                                | Default                                                                                                                |
| ------------------ | ---------------------- | ------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------- |
| `hostnameProperty` | `k3s.hostnameProperty` | Property name where to store the hostname. | k3s.hostname                                                                                                           |
| `hostnameCommand`  | `k3s.hostnameCommand`  | Command to determine hostname.             | ip -4 -o addr show&#124;grep -v 127.0.0.1&#124;grep -v 172.17.0.1&#124;tr -s ' '&#124;cut -d' ' -f4&#124;cut -d'/' -f1 |
| `imageRegistry`    | `k3s.imageRegistry`    | k3s image registry                         |                                                                                                                        |
| `imageRepository`  | `k3s.imageRepository`  | k3s image repository                       | docker.io/rancher/k3s                                                                                                  |
| `imageTag`         | `k3s.imageTag`         | k3s image tag                              | latest                                                                                                                 |
| `skipHostname`     | `k3s.skipApply`        | Skip applying kubectl manifests.           | false                                                                                                                  |
