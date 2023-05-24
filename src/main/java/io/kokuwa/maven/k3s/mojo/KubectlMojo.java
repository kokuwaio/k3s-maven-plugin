package io.kokuwa.maven.k3s.mojo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
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
import lombok.Setter;

/**
 * Mojo for kubectl.
 *
 * @since 0.2.0
 */
@Mojo(name = "kubectl", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class KubectlMojo extends K3sMojo {

	private static final Pattern PATTERN = Pattern
			.compile("customresourcedefinition\\.apiextensions\\.k8s\\.io/.* created");

	/**
	 * Stream logs of "kubectl" to maven logger.
	 *
	 * @since 0.2.0
	 * @deprecated Removed with 1.0.0
	 */
	@Deprecated(since = "0.11.0", forRemoval = true)
	@Parameter(property = "k3s.kubectl.streamLogs", defaultValue = "false")
	private boolean streamLogs;

	/**
	 * Path where to find manifest files.
	 *
	 * @since 0.2.0
	 */
	@Setter @Parameter(property = "k3s.kubectl.manifests", defaultValue = "src/test/k3s")
	private File manifests;

	/**
	 * Timeout in seconds to wait for kubectl finished.
	 *
	 * @since 0.4.0
	 */
	@Setter @Parameter(property = "k3s.kubectl.timeout", defaultValue = "30")
	private int kubectlTimeout;

	/**
	 * Timeout in seconds to wait for pods getting ready.
	 *
	 * @since 0.2.0
	 */
	@Setter @Parameter(property = "k3s.kubectl.podTimeout", defaultValue = "1200")
	private int podTimeout;

	/**
	 * Command to use for applying manifest files. Will process the directory recursively by default.
	 *
	 * @since 0.2.0
	 */
	@Setter @Parameter(property = "k3s.kubectl.command", defaultValue = "kubectl apply -R -f .")
	private String command;

	/**
	 * "kubectl" to use on host.
	 *
	 * @since 0.3.1
	 */
	@Setter @Parameter(property = "k3s.kubectl.path")
	private String kubectlPath;

	/**
	 * Skip applying kubectl manifests.
	 *
	 * @since 0.2.0
	 */
	@Setter @Parameter(property = "k3s.skipKubectl", defaultValue = "false")
	private boolean skipKubectl;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipKubectl)) {
			return;
		}

		// copy manifests to mounted directory (docker mode only)

		var source = manifests.toPath().toAbsolutePath();
		if (kubectlPath == null) {
			var destination = getManifestsDir().toAbsolutePath();
			try {
				if (Files.exists(source)) {
					if (Files.exists(destination)) {
						FileUtils.forceDelete(destination.toFile());
					}
					if (Files.isDirectory(source)) {
						FileUtils.copyDirectory(source.toFile(), destination.toFile());
					} else {
						Files.createDirectories(destination);
						Files.copy(source, destination.resolve(source.getFileName()));
					}
				}
			} catch (IOException e) {
				throw new MojoExecutionException("Failed to copy manifests", e);
			}
		}

		// execute command

		getLog().info("Execute: " + command);
		var result = kubectlPath == null ? execDocker() : execLocal();
		if (result.getExitCode() != 0) {
			var crd = result.getMessages().stream().map(PATTERN::matcher).anyMatch(Matcher::matches);
			if (crd) {
				getLog().info("Found CRDs created, but kubectl failed. Try again ...");
				result = kubectlPath == null ? execDocker() : execLocal();
			}
			if (result.getExitCode() != 0) {
				result.getMessages().forEach(getLog()::warn);
				throw new MojoExecutionException("Failed to execute manifests, exit code: " + result.getExitCode());
			}
		}

		// wait for stuff to be ready

		var kubernetes = getKubernetesClient();
		Await.await(getLog(), "k3s pods ready")
				.timeout(Duration.ofSeconds(podTimeout))
				.until(() -> kubernetes.isDeploymentsReady()
						&& kubernetes.isStatefulSetsReady()
						&& kubernetes.isPodsReady());
	}

	private ExecResult execDocker() throws MojoExecutionException {

		var containerOptional = getDocker().getContainer();
		if (containerOptional.isEmpty()) {
			throw new MojoExecutionException("No k3s container found");
		}

		var callback = new DockerLogCallback(getLog());
		return getDocker().exec("kubectl", containerOptional.get(), cmd -> cmd
				.withCmd("/bin/sh", "-c", command)
				.withWorkingDir("/k3s/manifests")
				.withEnv(List.of("KUBECONFIG=/k3s/kubeconfig.yaml")), callback, Duration.ofSeconds(kubectlTimeout));
	}

	private ExecResult execLocal() throws MojoExecutionException {

		var processBuilder = new ProcessBuilder();
		processBuilder.environment().put("KUBECONFIG", getKubeConfig().toString());
		processBuilder.command("/bin/sh", "-c", command);
		processBuilder.directory(manifests);

		try {
			var process = processBuilder.start();
			var exitCode = process.waitFor();
			var logs = List.of(new String(process.getInputStream().readAllBytes()).split("\n"));
			return new ExecResult(exitCode, logs);
		} catch (InterruptedException | IOException e) {
			throw new MojoExecutionException("Failed to execute manifests", e);
		}
	}

	// setter

	public void setStreamLogs(boolean streamLogs) {
		this.streamLogs = streamLogs;
		if (streamLogs == true) {
			getLog().warn("Used deprecated confuguration `streamLogs`, use `debug` instead");
			this.setDebug(true);
		}
	}
}
