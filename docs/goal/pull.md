# k3s:pull

Pull k3s image.

| Name | User Property | Description | Default |
| -----| ------------- | ----------- | ------- |
| `imageRegistry` | `k3s.imageRegistry` | k3s image registry | |
| `imageRepository` | `k3s.imageRepository` | k3s image repository | rancher/k3s |
| `imageTag` | `k3s.imageTag` | k3s image tag | v1.23.4-k3s1 |
| `pullAlways` | `k3s.pullAlways` | Always pull images | false |
| `pullTimeout` | `k3s.pullTimeout` | pull images in seconds | 300 |
| `pullAdditionalImages` | `k3s.pullAdditionalImages` | list of additional images to pull | [] |
| `skipPull` | `k3s.skipPull` | Skip pulling k3s image. | false |
