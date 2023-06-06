package io.kokuwa.maven.k3s.mojo;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
	 * Subdir of {@link #manifests} to execute
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.subdir")
	private String subdir;

	/**
	 * Use kustomize while applying manifest files.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.kustomize", defaultValue = "false")
	private boolean kustomize;

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

		// verify container

		if (getDocker().getContainer().isEmpty()) {
			throw new MojoExecutionException("No k3s container found");
		}

		// wait for service account, see https://github.com/kubernetes/kubernetes/issues/66689

		Await.await(getLog(), "k3s service account ready").until(() -> !getDocker()
				.exec("kubectl", "get", "sa", "default", "--ignore-not-found", "--output=name")
				.isEmpty());

		// execute command

		var result = apply();
		if (result.exitCode() != 0 && result.output().stream().anyMatch(l -> l.endsWith("CRDs are installed first"))) {
			getLog().info("Found CRDs created, but kubectl failed. Try again ...");
			result = apply();
		}
		result.verify();

		// wait for stuff to be ready

		waitFor("statefulset", List.of("kubectl", "rollout", "status"));
		waitFor("deployment", List.of("kubectl", "rollout", "status"));
		waitFor("job", List.of("kubectl", "wait", "--for=condition=complete"));
		waitFor("pod", List.of("kubectl", "wait", "--for=condition=ready"));
	}

	private Task apply() throws MojoExecutionException {

		var path = Paths.get("/tmp/manifests");
		var subPath = subdir == null ? path : path.resolve(subdir);

		var command = new ArrayList<String>();
		command.add("kubectl");
		command.add("apply");
		if (kustomize) {
			command.add("--kustomize=" + subPath);
		} else {
			command.add("--filename=" + subPath);
			command.add("--recursive");
		}

		getDocker().copyToContainer(manifests, path);
		return getDocker().execWithoutVerify(timeout, command);
	}

	private void waitFor(String kind, List<String> command) throws MojoExecutionException {

		var resources = getDocker()
				.exec("kubectl", "get", kind,
						"--all-namespaces",
						"--no-headers",
						"--output=custom-columns=:.metadata.namespace,:.metadata.name")
				.stream().map(resource -> resource.split("\\s+")).collect(Collectors.toList());

		if ("pod".equals(kind)) { // remove managed pods from deployments/statefulsets
			resources.removeIf(r -> Pattern.matches(".*-[0-9]+", r[1]));
			resources.removeIf(r -> Pattern.matches(".*(-[a-z0-9]{8,10})?-[a-z0-9]{5}", r[1]));
		}

		for (var resource : resources) {
			var namespace = resource[0];
			var name = resource[1];
			var tmp = new ArrayList<>(command);
			tmp.add(kind);
			tmp.add(name);
			tmp.add("--namespace=" + namespace);
			tmp.add("--timeout=" + timeout.getSeconds() + "s");
			try {
				getLog().debug(kind + " " + namespace + "/" + name + " ... waiting");
				getDocker().exec(timeout.plusSeconds(10), tmp);
				getLog().info(kind + " " + namespace + "/" + name + " ... ready");
			} catch (MojoExecutionException e) {
				getDocker().exec("kubectl", "get", "--output=yaml", "--namespace=" + namespace, kind, name);
				throw e;
			}
		}
	}

	// setter

	public void setManifests(File manifests) {
		this.manifests = manifests.toPath().toAbsolutePath();
	}

	public void setSubdir(String subdir) {
		this.subdir = subdir;
	}

	public void setKustomize(boolean kustomize) {
		this.kustomize = kustomize;
	}

	public void setTimeout(int timeout) {
		this.timeout = Duration.ofSeconds(timeout);
	}

	public void setSkipApply(boolean skipApply) {
		this.skipApply = skipApply;
	}
}
