package io.kokuwa.maven.k3s.mojo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.kokuwa.maven.k3s.util.Await;
import io.kokuwa.maven.k3s.util.DockerLogCallback;
import io.kokuwa.maven.k3s.util.ExecResult;

/**
 * Mojo for kubectl apply.
 *
 * @author stephan.schnabel@posteo.de
 * @since 1.0.0
 */
@Mojo(name = "apply", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class ApplyMojo extends K3sMojo {

	/**
	 * Path where to find manifest files to apply.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.manifests", defaultValue = "${project.basedir}/src/test/k3s")
	private Path manifests;

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

		// copy manifests to mounted directory (docker mode only)

		try {
			if (Files.exists(manifests)) {
				if (Files.exists(getManifestsDir())) {
					FileUtils.forceDelete(getManifestsDir().toFile());
				}
				if (Files.isDirectory(manifests)) {
					FileUtils.copyDirectory(manifests.toFile(), getManifestsDir().toFile());
				} else {
					Files.createDirectories(getManifestsDir());
					Files.copy(manifests, getManifestsDir().resolve(manifests.getFileName()));
				}
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to copy manifests", e);
		}

		// wait for service account, see https://github.com/kubernetes/kubernetes/issues/66689

		Await.await(getLog(), "k3s service account ready").until(getKubernetesClient()::isServiceAccountReady);

		// execute command

		var result = apply();
		if (result.getExitCode() != 0) {
			var crdPattern = Pattern.compile("customresourcedefinition\\.apiextensions\\.k8s\\.io/.* created");
			if (result.getMessages().stream().map(crdPattern::matcher).anyMatch(Matcher::matches)) {
				getLog().info("Found CRDs created, but kubectl failed. Try again ...");
				result = apply();
			}
		}
		if (result.getExitCode() != 0) {
			result.getMessages().forEach(getLog()::warn);
			throw new MojoExecutionException("Failed to execute manifests, exit code: " + result.getExitCode());
		}

		// wait for stuff to be ready

		var kubernetes = getKubernetesClient();
		Await.await(getLog(), "k3s pods ready").timeout(timeout).until(
				() -> kubernetes.isDeploymentsReady()
						&& kubernetes.isStatefulSetsReady()
						&& kubernetes.isPodsReady());
	}

	private ExecResult apply() throws MojoExecutionException {

		var path = Paths.get("/k3s/manifests");
		var command = new ArrayList<String>();
		command.add("kubectl");
		command.add("apply");
		if (kustomize) {
			command.add("--kustomize=" + path);
		} else {
			command.add("--filename=" + path);
			command.add("--recursive");
		}

		var callback = new DockerLogCallback(getLog());
		return getDocker().exec(command.toString(), getDocker().getContainer().get(), cmd -> cmd
				.withCmd(command.toArray(new String[command.size()])), callback, timeout);
	}

	// setter

	public void setManifests(File manifests) {
		this.manifests = manifests.toPath().toAbsolutePath();
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
