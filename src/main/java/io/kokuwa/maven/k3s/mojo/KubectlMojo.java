package io.kokuwa.maven.k3s.mojo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.LoggerFactory;

import io.kokuwa.maven.k3s.K3sMojo;
import io.kokuwa.maven.k3s.util.Await;
import io.kokuwa.maven.k3s.util.DockerLogCallback;
import lombok.Setter;

/**
 * Mojo for kubectl.
 */
@Mojo(name = "kubectl", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class KubectlMojo extends K3sMojo {

	/** Stream logs of `kubectl` to maven logger. */
	@Setter @Parameter(property = "k3s.kubectl.streamLogs", defaultValue = "false")
	private boolean streamLogs;

	/** Path where to find manifest files. */
	@Setter @Parameter(property = "k3s.kubectl.manifests", defaultValue = "src/test/k3s")
	private File manifests;

	/** Timeout in seconds to wait for pods getting ready. */
	@Setter @Parameter(property = "k3s.kubectl.podTimeout", defaultValue = "300")
	private int podTimeout;

	/** Command to use for applying kustomize files. */
	@Setter @Parameter(property = "k3s.kubectl.command", defaultValue = "kubectl apply -f .")
	private String command;

	/** Skip applying kubectl manifests. */
	@Setter @Parameter(property = "k3s.skipKubectl", defaultValue = "false")
	private boolean skipKubectl;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipKubectl)) {
			return;
		}

		// get container id

		var optionalContainerId = dockerUtil().getContainerId();
		if (optionalContainerId.isEmpty()) {
			throw new MojoExecutionException("Container not found");
		}
		var containerId = optionalContainerId.get();

		// copy manifests to working directory

		var source = manifests.toPath().toAbsolutePath();
		var destination = getWorkingDir().resolve("manifests").toAbsolutePath();
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

		// exec

		log.info(command);
		var execId = dockerClient().execCreateCmd(containerId)
				.withCmd("/bin/sh", "-c", command)
				.withWorkingDir("/k3s/manifests")
				.withEnv(List.of("KUBECONFIG=/k3s/kubeconfig.yaml"))
				.withAttachStdout(true)
				.withAttachStderr(true)
				.exec().getId();

		var callback = new DockerLogCallback(LoggerFactory.getLogger("io.kokuwa.maven.k3s.docker.kubectl"), streamLogs);
		dockerClient().execStartCmd(execId).exec(callback);
		Await.await(command).onTimeout(callback::replayOnWarn).until(callback::isCompleted);

		var response = dockerClient().inspectExecCmd(execId).exec();
		if (response.getExitCodeLong() != 0) {
			callback.replayOnWarn();
			throw new MojoExecutionException("kubectl returned exit code " + response.getExitCodeLong());
		}

		// wait for pods to be ready

		Await.await("k3s pods ready").timeout(Duration.ofSeconds(podTimeout)).until(kubernetes()::isPodsReady);
		log.debug("k3s pods ready");
	}
}