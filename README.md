# k3s Maven Plugin

[![Apache License 2.0](https://img.shields.io/github/license/kokuwaio/helm-maven-plugin)](https://github.com/kokuwaio/helm-maven-plugin/blob/main/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/io.kokuwa.maven/k3s-maven-plugin)](https://central.sonatype.com/namespace/io.kokuwa.maven)
[![Build](https://img.shields.io/github/actions/workflow/status/kokuwaio/k3s-maven-plugin/build.yaml?branch=main)](https://github.com/kokuwaio/k3s-maven-plugin/actions/workflows/build.yaml?query=branch%3Amain)

This is a plugin to manage k3s for integration tests.

## Why `k3s-maven-plugin`?

For unit testing [JUnit5](https://junit.org/junit5/docs/current/user-guide/) and [Testcontainers](https://www.testcontainers.org/) (e.g. for PostgreSQL) can be used. This is not sufficient for complex setups or running systems for ui development outside of JUnit context. `docker-compose` is a common technology to describe this setups.

If your production system is Kubernetes it would be better to use some kind of Kubernetes for integration tests:

* [`k3s`](https://k3s.io/)
* [`kind`](https://kind.sigs.k8s.io/)
* [`minikube`](https://minikube.sigs.k8s.io/docs/)

Because `k3s` has a very fast startup and can run in docker this plugin relies on `k3s`. This plugin runs `k3s` as docker container (like `k3d`). As alternative it was considered to use `k3s` in rootless mode. This assumes packages like `newguimap` which are not installed everywhere. Feel free to create a PR for adding a non-docker mode of this plugin.

If you don't like to use this plugin you can:

* use `docker-compose` with testcontainers [Docker Compose Module](https://www.testcontainers.org/modules/docker_compose/)
* use `docker-compose` with [docker-compose-maven-plugin](https://github.com/syncdk/docker-compose-maven-plugin)
* use `k3d` as wrapper for `k3s` in docker with [exec-maven-plugin](https://www.mojohaus.org/exec-maven-plugin)
* start `k3s` direct with [exec-maven-plugin](https://www.mojohaus.org/exec-maven-plugin)
* start `k3s` in docker with [docker-maven-plugin](https://github.com/fabric8io/docker-maven-plugin)
* handle lifecycle out of maven

## Goals

| Goal                                  | Description                     | Default Lifecycle Phase |
| ------------------------------------- | ------------------------------- | ----------------------- |
| [`k3s:run`](docs/goal/run.md)         | Create and start k3s container  | pre-integration-test    |
| [`k3s:image`](docs/goal/image.md)     | Prepare images for containerd   | pre-integration-test    |
| [`k3s:apply`](docs/goal/apply.md)     | Run kubectl aplpy               | pre-integration-test    |
| [`k3s:restart`](docs/goal/apply.md)   | Restart resources               | pre-integration-test    |
| [`k3s:rm`](docs/goal/rm.md)           | Stop and remove k3s containers  | post-integration-test   |

## Examples

To plugin is tested with `maven-invoker-plugin`. The testcases can be used as examples.

### [Pod using HostPort](/src/it/pod-with-hostport)

* manifest are applied with `k3s:apply`
* `k3s` and pod image is always pulled via `k3s:iamge`, pod has [imagePullPolicy: Never](/src/it/pod-with-hostport/src/test/k3s/pod.yaml#L9)
* Pod is running with [hostport](/src/it/pod-with-hostport/src/test/k3s/pod.yaml#L12) 8080
* [test](/src/it/pod-with-hostport/src/test/java/io/kokuwa/maven/k3s/PodIT.java#L21) uses `http://127.0.0.1:8080` as endpoint

### [Local image using jib as Pod with HostPort](src/it/pod-with-local-image-from-tar)

* manifest are applied with `k3s:apply`
* image is build with [jib-maven-plugin](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin) to tar file
* image is imported from tar file
* image is [Never](/src/it/pod-with-local-image-from-docker/src/test/k3s/pod.yaml#L9) pulled from registries
* Pod is running with [hostport](/src/it/pod-with-local-image-from-docker/src/test/k3s/pod.yaml#L13) 8080
* [test](/src/it/pod-with-local-image-from-docker/src/test/java/io/kokuwa/maven/k3s/PodIT.java#L20) uses `http://127.0.0.1:8080` as endpoint

### [Local image using jib as Pod with HostPort](src/it/pod-with-local-image-from-docker)

* manifest are applied with `k3s:apply`
* image is build with [jib-maven-plugin](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin) to local docker deamon
* image is imported from local docker deamon
* image is [Never](/src/it/pod-with-local-image-from-docker/src/test/k3s/pod.yaml#L9) pulled from registries
* Pod is running with [hostport](/src/it/pod-with-local-image-from-docker/src/test/k3s/pod.yaml#L13) 8080
* [test](/src/it/pod-with-local-image-from-docker/src/test/java/io/kokuwa/maven/k3s/PodIT.java#L20) uses `http://127.0.0.1:8080` as endpoint

### [Traefik and Dashboard](src/it/traefik)

* manifest are applied with `k3s:apply` using custom command with kustomize
* Traefik for subdomains of `127.0.0.1.nip.io` with [LoadBalancer](/src/it/traefik/src/test/k3s/traefik/service.yaml#L18) on port 8080
* Traefik Admin available at [http://traefik.127.0.0.1.nip.io:8080](http://traefik.127.0.0.1.nip.io:8080)
* Kubernetes Dashboard available at [http://dashboard.127.0.0.1.nip.io:8080](http://dashboard.127.0.0.1.nip.io:8080)
* Deployment with Ingress [http://echo.127.0.0.1.nip.io:8080](http://echo.127.0.0.1.nip.io:8080)
* [test](/src/it/traefik/src/test/java/io/kokuwa/maven/k3s/PodIT.java#L21) uses `http://echo.127.0.0.1.nip.io:8080` as endpoint

### [Istio and Dashboard](src/it/istio)

* manifest are applied with `k3s:apply` using custom command with kustomize
* Istio for subdomains of `127.0.0.1.nip.io` with [LoadBalancer](/src/it/istio/src/test/k3s/istio/istio.yaml#L9334) on port 80
* Kubernetes Dashboard available at [http://dashboard.127.0.0.1.nip.io:80](http://dashboard.127.0.0.1.nip.io)
* Deployment with Ingress [http://echo.127.0.0.1.nip.io:80](http://echo.127.0.0.1.nip.io:80)
* [test](/src/it/istio/src/test/java/io/kokuwa/maven/k3s/PodIT.java#L21) uses `http://echo.127.0.0.1.nip.io:80` as endpoint

### [PostgreSQL using PVC with HostPort](src/it/postgresql-with-pvc-and-hostport)

* manifest are applied with `k3s:apply` using custom command with kustomize
* PostgreSQL is running with [hostport](/src/it/postgresql-with-pvc-and-hostport/src/test/k3s/pod.yaml#L15) 5432
* [test](/src/it/postgresql-with-pvc-and-hostport/src/test/java/io/kokuwa/maven/k3s/PostgreIT.java#L26) uses `http://127.0.0.1:5432` as endpoint

### [Kafka/Kafka Web UI with HostPort](src/it/kafka-with-hostport)

* manifest are applied with `k3s:apply`
* Kafka is running with [hostport](/src/it/kafka-with-hostport/src/test/k3s/kafka.yaml#L29) 9091
* [Kafka Web UI](https://github.com/obsidiandynamics/kafdrop) available at [http://kafdrop.127.0.0.1.nip.io:9000](http://kafdrop.127.0.0.1.nip.io:9000)
* [test](/src/it/kafka-with-hostport/src/test/java/io/kokuwa/maven/k3s/KafkaIT.java#L30) uses `http://kafka.127.0.0.1.nip.io:9091` as endpoint

### [Cert-Manager](src/it/cert-manager)

* manifest are generated with `helm template`, using the [helm-maven-plugin](https://github.com/kokuwaio/helm-maven-plugin)
* manifest are applied with `k3s:apply`
* test itself doesn't assert anything, but success verifies that the job completed and was taken as success.

### Usage out of Build

Add to your `settings.xml` (or prefix goals with groupId):

```xml
<pluginGroups>
 ...
 <pluginGroup>io.kokuwa.maven</pluginGroup>
</pluginGroups>
```

Start k3s with deployments for manual testing:

```sh
mvn k3s:run k3s:apply \
  -Dk3s.portBindings=8080:8080 \
  -Dk3s.manifests=src/it/traefik/src/test/k3s \
  -Dk3s.kustomize'
  -Dk3s.debug
```

Now you can access this urls:

* Traefik Admin: [http://traefik.127.0.0.1.nip.io:8080](http://traefik.127.0.0.1.nip.io:8080)
* Kubernetes Dashboard: [http://dashboard.127.0.0.1.nip.io:8080](http://dashboard.127.0.0.1.nip.io:8080)
* Echo: [http://echo.127.0.0.1.nip.io:8080](http://echo.127.0.0.1.nip.io:8080)

Use external `kubectl`:

```sh
export KUBECONFIG=~/.kube/k3s-maven-plugin/mount/kubeconfig.yaml && kubectl get all --all-namespaces
```

Stop k3s after manual testing:

```sh
mvn k3s:rm
```
