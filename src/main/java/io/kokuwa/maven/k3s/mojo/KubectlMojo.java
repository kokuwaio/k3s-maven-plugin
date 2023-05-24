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

		// verify container

		if (getDocker().getContainer().isEmpty()) {
			throw new MojoExecutionException("No k3s container found");
		}

		// copy manifests to mounted directory (docker mode only)

		var source = manifests.toPath().toAbsolutePath();
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

		// wait for service account, see https://github.com/kubernetes/kubernetes/issues/66689

		Await.await(getLog(), "k3s service account ready").until(getKubernetesClient()::isServiceAccountReady);

		// execute command

		getLog().info("Execute: " + command);
		var result = exec();
		if (result.getExitCode() != 0) {
			var crd = result.getMessages().stream().map(PATTERN::matcher).anyMatch(Matcher::matches);
			if (crd) {
				getLog().info("Found CRDs created, but kubectl failed. Try again ...");
				result = exec();
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

	private ExecResult exec() throws MojoExecutionException {
		var callback = new DockerLogCallback(getLog());
		return getDocker().exec("kubectl", getDocker().getContainer().get(), cmd -> cmd
				.withCmd("/bin/sh", "-c", command)
				.withWorkingDir("/k3s/manifests")
				.withEnv(List.of("KUBECONFIG=/k3s/kubeconfig.yaml")), callback, Duration.ofSeconds(kubectlTimeout));
	}
}
