package io.kokuwa.maven.k3s.mojo;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.kokuwa.maven.k3s.util.Await;
import io.kokuwa.maven.k3s.util.Task;

/**
 * Mojo for kubectl apply.
 *
 * @author stephan.schnabel@posteo.de
 * @since 1.0.0
 */
@Mojo(name = "apply", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class ApplyMojo extends K3sMojo {

	/**
	 * Path where to find manifest files to apply. This files are copied to docker container.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.manifests", defaultValue = "${project.basedir}/src/test/k3s")
	private Path manifests;

	/**
	 * Path for {@link #manifests} inside the k3s container.
	 *
	 * @since 1.0.0
	 */
	@Parameter(readonly = true, defaultValue = "/tmp/manifests")
	private Path path;

	/**
	 * Subdir of {@link #manifests} to execute inside the k3s container.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.subdir")
	private Path subdir;

	/**
	 * Timeout in seconds to wait for resources getting ready.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.timeout", defaultValue = "300")
	private Duration timeout;

	/**
	 * Skip applying kubectl manifests.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.skipApply", defaultValue = "false")
	private boolean skipApply;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipApply)) {
			return;
		}

		// verify container and copy manifests

		if (getDocker().getContainer().isEmpty()) {
			throw new MojoExecutionException("No k3s container found");
		}
		getDocker().copyToContainer(manifests, toLinuxPath(path), timeout);

		// wait for service account, see https://github.com/kubernetes/kubernetes/issues/66689

		var serviceAccount = new String[] { "kubectl", "get", "sa", "default", "--ignore-not-found", "--output=name" };
		if (getDocker().exec(serviceAccount).isEmpty()) {
			log.info("");
			log.info("No service account found, waiting for sa ...");
			Await.await(log, "k3s service account ready").until(() -> !getDocker().exec(serviceAccount).isEmpty());
			log.info("Service account found, continue ...");
			log.info("");
		}

		// wait for node getting ready

		getDocker().exec("kubectl", "wait", "--for=condition=Ready", "node", "k3s");
	
		// check taints

		var taints = getDocker()
				.exec("kubectl", "get", "nodes", 
						"-o=jsonpath={range .items[*]}{.spec.taints[?(@.effect==\"NoSchedule\")].key}{\"\\n\"}{end}")
				.stream().map(String::strip).filter(s -> !s.isEmpty()).collect(Collectors.toList());
		if (!taints.isEmpty()) {
			log.error("Found node taints with effect NoSchedule: {}", taints);
			throw new MojoExecutionException("Node has taints " + taints + " with effect NoSchedule");
		}

		// execute command

		var result = apply();
		if (result.exitCode() != 0 && result.output().stream().anyMatch(l -> l.endsWith("CRDs are installed first"))) {
			log.info("Found CRDs created, but kubectl failed. Try again ...");
			result = apply();
		}
		result.verify();

		// wait for stuff to be ready

		var tasks = Map
				.of(
						"statefulset", List.of("kubectl", "rollout", "status"),
						"deployment", List.of("kubectl", "rollout", "status"),
						"job", List.of("kubectl", "wait", "--for=condition=complete"),
						"pod", List.of("kubectl", "wait", "--for=condition=ready"))
				.entrySet().stream().parallel().flatMap(this::waitFor).collect(Collectors.toSet());
		try {
			var success = true;
			var pool = Executors.newWorkStealingPool();
			var futures = tasks.stream().collect(Collectors.toMap(Entry::getKey, e -> pool.submit(e.getValue())));
			var missing = new AtomicReference<>(futures.keySet().stream().sorted().collect(Collectors.toList()));
			pool.submit(() -> {
				while (!futures.values().stream().allMatch(Future::isDone)) {
					Thread.sleep(15000);
					var newMissing = futures.entrySet().stream()
							.filter(f -> !f.getValue().isDone())
							.map(Entry::getKey).sorted().collect(Collectors.toList());
					var oldMissing = missing.getAndSet(newMissing);
					if (!newMissing.isEmpty()) {
						if (oldMissing.equals(newMissing)) {
							log.debug("Still waiting for: {}", missing);
						} else {
							log.info("Still waiting for: {}", missing);
						}
					}
				}
				return null;
			});

			for (var future : futures.entrySet()) {
				success &= future.getValue().get();
			}
			if (!success) {
				throw new MojoExecutionException("Failed to wait for resources, see previous log");
			}
		} catch (InterruptedException | ExecutionException e) {
			throw new MojoExecutionException("Failed to wait for resources", e);
		}
	}

	private Task apply() throws MojoExecutionException {

		var subPath = toLinuxPath(subdir == null ? path : path.resolve(subdir));
		var kustomizePath = subdir == null ? manifests : manifests.resolve(subdir);
		var kustomize = Files.isRegularFile(kustomizePath.resolve("kustomization.yml"))
				|| Files.isRegularFile(kustomizePath.resolve("kustomization.yaml"));

		var command = new ArrayList<String>();
		command.add("kubectl");
		command.add("apply");
		if (kustomize) {
			command.add("--kustomize=" + subPath);
		} else {
			command.add("--filename=" + subPath);
			command.add("--recursive");
		}

		log.info(command.stream().collect(Collectors.joining(" ")));
		return getDocker().execWithoutVerify(timeout, command);
	}

	private Stream<Entry<String, Callable<Boolean>>> waitFor(Entry<String, List<String>> entry) {
		try {

			var kind = entry.getKey();
			var resources = getDocker()
					.exec("kubectl", "get", kind,
							"--all-namespaces",
							"--no-headers",
							"--output=custom-columns=:.metadata.namespace,:.metadata.name")
					.stream().map(resource -> resource.split("\\s+")).collect(Collectors.toList());

			if ("pod".equals(kind)) {
				// remove pods from local storage provider
				resources.removeIf(r -> "kube-system".equals(r[0]) && r[1].startsWith("helper-pod-create-pvc-"));
				// remove managed pods from deployments/statefulsets
				resources.removeIf(r -> Pattern.matches(".*-[0-9]+", r[1]));
				resources.removeIf(r -> Pattern.matches(".*(-[a-z0-9]{8,10})?-[a-z0-9]{5}", r[1]));
			}

			return resources.stream().map(resource -> {
				var namespace = resource[0];
				var name = resource[1];
				var tmp = new ArrayList<>(entry.getValue());
				tmp.add(kind);
				tmp.add(name);
				tmp.add("--namespace=" + namespace);
				tmp.add("--timeout=" + timeout.getSeconds() + "s");
				var representation = "default".equals(namespace) ? name : namespace + "/" + name;
				return Map.entry(representation, (Callable<Boolean>) () -> {
					try {
						log.debug("{} {} ... waiting", kind, representation);
						getDocker().exec(timeout.plusSeconds(10), tmp);
						log.info("{} {} ... ready", kind, representation);
						return true;
					} catch (MojoExecutionException e) {
						getDocker().exec("kubectl", "get", "--output=yaml", "--namespace=" + namespace, kind, name);
						return false;
					}
				});
			});

		} catch (MojoExecutionException e) {
			return Stream.of(Map.entry("exception", () -> false));
		}
	}

	// setter

	public void setManifests(File manifests) {
		this.manifests = manifests.toPath().toAbsolutePath();
	}

	public void setPath(String path) {
		this.path = Paths.get(path);
	}

	public void setSubdir(String subdir) {
		this.subdir = subdir == null ? null : Paths.get(subdir);
	}

	public void setTimeout(int timeout) {
		this.timeout = Duration.ofSeconds(timeout);
	}

	public void setSkipApply(boolean skipApply) {
		this.skipApply = skipApply;
	}
}
