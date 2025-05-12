# k3s:run

Start and run k3s container.

| Name | User Property | Description | Default |
| -----| ------------- | ----------- | ------- |
| `imageRegistry` | `k3s.imageRegistry` | k3s image registry | |
| `imageRepository` | `k3s.imageRepository` | k3s image repository | docker.io/rancher/k3s |
| `imageTag` | `k3s.imageTag` | k3s image tag | latest |
| `portBindings` | `k3s.portBindings` | Skip starting k3s container. | [] |
| `portKubeApi` | `k3s.portKubeApi` | KubeApi port to expose to host. | 6443 |
| `k3s.clusterDomain` | `k3s.clusterDomain` | Cluster Domain. | |
| `k3s.clusterDns` | `k3s.clusterDns` |  IPv4 Cluster IP for coredns service. | |
| `k3s.clusterCidr` | `k3s.clusterCidr` | IPv4/IPv6 network CIDRs to use for pod IPs. | |
| `k3s.serviceCidr` | `k3s.serviceCidr` | IPv4/IPv6 network CIDRs to use for service IPs.| |
| `disableServicelb` | `k3s.disableServicelb` | Disable service load balancer. | false |
| `disableHelmController` | `k3s.disableZHelmController` | Disable helm controller. | true |
| `disableLocalStorage` | `k3s.disableLocalStorage` | Disable local storage. | true |
| `disableMetricsServer` | `k3s.disableMetricsServer` | Disable metrics server. | true |
| `disableTraefik` | `k3s.disableTraefik` | Disable traefik. | true |
| `disableCoredns` | `k3s.disableCoredns` | Disable coredns. | false |
| `disableCloudController` | `k3s.disableCloudController` | Disable cloud-controller. | true |
| `disableNetworkPolicy` | `k3s.disableNetworkPolicy` | Disable network-policy. | true |
| `failIfExists` | `k3s.failIfExists` | Fail if docker container from previous run exists. E.g. with `mvn k3s:rm` | true |
| `replaceIfExists` | `k3s.replaceIfExists` | Replace existing docker container from previous run. | false |
| `nodeTimeout` | `k3s.nodeTimeout` | Timeout in seconds to wait for nodes getting ready. | 30 |
| `kubeconfig` | `k3s.kubeconfig` | Path where to place kubectl config for external usage. | ${project.build.directory}/k3s.yaml |
| `registries` | `k3s.registries` | Path to "registry.yaml" to mount to "/etc/rancher/k3s/registries.yaml". | `null` |
| `disableDefaultRegistryEndpoint` | `k3s.disableDefaultRegistryEndpoint` | Disables containerd's fallback default registry endpoint when a mirror is configured for that registry. | false |
| `skipRun` | `skipRun` | Skip running of k3s. | false |
| `debug` | `k3s.debug` | Stream logs of docker and kubectl. | false |
| `taskTimeout` | `k3s.taskTimeout` | Default timeout for docker tasks in seconds. | 30 |
