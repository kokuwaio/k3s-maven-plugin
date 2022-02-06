# k3s Maven Plugin

[![License](https://img.shields.io/github/license/kokuwaio/k3s-maven-plugin.svg?label=License)](https://github.com/kokuwaio/k3s-maven-plugin/blob/main/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/io.kokuwa.maven/k3s-maven-plugin.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.kokuwa.maven%22%20AND%20a:%22k3s-maven-plugin%22)
[![Build](https://img.shields.io/github/workflow/status/kokuwaio/k3s-maven-plugin/Snapshot?label=Build)](https://github.com/kokuwaio/k3s-maven-plugin/actions/workflows/snapshot.yaml?label=Build)

This is a plugin to manage k3s for integration tests.

## Why `k3s-maven-plugin`?

For unit testing [JUnit5](https://junit.org/junit5/docs/current/user-guide/) and [Testcontainers](https://www.testcontainers.org/) (e.g. for Postgres) can be used. This is not sufficient for complex setups or running systems for ui development outside of junit context. `docker-compose` was a common technology to describe this setups.

If your production system is Kubernetes it would be better to use some kind of Kubernetes for integration tests:

* [`k3s`](https://k3s.io/)
* [`kind`](https://kind.sigs.k8s.io/)
* [`minikube`](https://minikube.sigs.k8s.io/docs/)

Becaues `k3s` has a very fast startup and can run in docker this plugin relies on `k3s`.

If you don't like to use this plugin you can:

* use `docker-compose` with testcontainers [Docker Compose Module](https://www.testcontainers.org/modules/docker_compose/)
* use `docker-compose` with [docker-compose-maven-plugin](https://github.com/syncdk/docker-compose-maven-plugin)
* start `k3s` direct with [exec-maven-plugin](https://www.mojohaus.org/exec-maven-plugin)
* start `k3s` in docker with [docker-maven-plugin](https://github.com/fabric8io/docker-maven-plugin)
* handle lifecycle out of maven

## Goals

| Goal                                       | Description                      | Default Lifecycle Phase |
| ------------------------------------------ | -------------------------------- | ----------------------- |
| [`k3s:pull`](docs/goal/pull.md)            | Pull k3s image                   | pre-integration-test    |
| [`k3s:start`](docs/goal/start.md)          | Create and start k3s container   | pre-integration-test    |
| [`k3s:apply`](docs/goal/apply.md)          | Apply plain manifest files       | pre-integration-test    |
| [`k3s:kustomize`](docs/goal/kustomize.md)  | Apply kustomize manifest files   | pre-integration-test    |
| [`k3s:stop`](docs/goal/stop.md)            | Stop k3s container               |                         |
| [`k3s:rm`](docs/goal/rm.md)                | Stop and destroy k3s containers  | post-integration-test   |

## Examples

To plugin is tested with `maven-invoker-plugin`. The testcases can be uses as examples.

### [IntegrationTest with Pod using HostPort](/src/it/pod-with-hostport)

* manifest are applied with `k3s:apply`
* Pod is running with [hostport](/src/it/pod-with-hostport/src/test/k3s/pod.yaml#L12) 8080
* [test](/src/it/pod-with-hostport/src/test/java/io/kokuwa/maven/k3s/PodIT.java#L21) uses `http://127.0.0.1:8080` as endpoint
  
### [IntegrationTest with Traefik and Dashboard](src/it/pod-with-traefik-and-dasboard)

* manifest are applied with `k3s:kustomize`
* Traefik for subdomains of `127.0.0.1.nip.io` with [hostport](/src/it/pod-with-traefik-and-dasboard/src/test/k3s/traefik/deployment.yaml#L35) 8080
* Traefik Admin available at [http://traefik.127.0.0.1.nip.io:8080](http://traefik.127.0.0.1.nip.io:8080)
* Kubernetes Dashboard available at [http://dashboard.127.0.0.1.nip.io:8080](http://dashboard.127.0.0.1.nip.io)
* Deployment with Ingress [http://echo.127.0.0.1.nip.io:8080](http://echo.127.0.0.1.nip.io:8080)
* [test](/src/it/pod-with-traefik-and-dasboard/src/test/java/io/kokuwa/maven/k3s/PodIT.java#L21) uses `http://echo.127.0.0.1.nip.io:8080` as endpoint

### [PostgreSQL using PVC with HostPort](src/it/postgresql-with-pvc-and-hostport)

* manifest are applied with `k3s:kustomize`
* PostgreSQL is running with [hostport](/src/it/postgresql-with-pvc-and-hostport/src/test/k3s/pod.yaml#L15) 5432
* [test](/src/it/postgresql-with-pvc-and-hostport/src/test/java/io/kokuwa/maven/k3s/PostgreIT.java#L26) uses `http://127.0.0.1:5432` as endpoint

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
mvn k3s:pull k3s:start k3s:kustomize \
  -Dk3s.portBindings=8080:8080 \
  -Dk3s.kubectl.manifests=src/it/pod-with-traefik-and-dasboard/src/test/k3s \
  -Dk3s.streamLogs
```

Now you can access this urls:

* Traefik Admin: [http://traefik.127.0.0.1.nip.io:8080](http://traefik.127.0.0.1.nip.io:8080)
* Kubernetes Dashboard: [http://dashboard.127.0.0.1.nip.io:8080](http://dashboard.127.0.0.1.nip.io:8080)
* Echo: [http://echo.127.0.0.1.nip.io:8080](http://echo.127.0.0.1.nip.io:8080)

Use external `kubectl`:

```sh
export KUBECONFIG=target/k3s/kubeconfig.yaml && kubectl get all --all-namespaces
```

Stop k3s after manual testing:

```sh
mvn k3s:rm
```
