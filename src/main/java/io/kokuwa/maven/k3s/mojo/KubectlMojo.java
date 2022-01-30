package io.kokuwa.maven.k3s.mojo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.kokuwa.maven.k3s.K3sMojo;
import io.kokuwa.maven.k3s.util.Await;
import io.kokuwa.maven.k3s.util.DockerLogCallback;
import lombok.Getter;
import lombok.Setter;

/**
 * Mojo to apply manifests.
 */
@Mojo(name = "kubectl", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class KubectlMojo extends K3sMojo {

	private static final String COMMAND = "kubectl apply -f .";
	private static final String MANIFESTS = "src/test/k3s";

	@Getter	@Setter	@Parameter(property = "k3s.kubectl.streamLogs", defaultValue = "true")
	private boolean kubectlStreamLogs = true;
	@Getter	@Setter	@Parameter(property = "k3s.kubectl.manifests", defaultValue = MANIFESTS)
	private String kubectlManifests = MANIFESTS;
	@Getter	@Setter	@Parameter(property = "k3s.kubectl.command", defaultValue = COMMAND)
	private String kubectlCommand = COMMAND;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip()) {
			return;
		}

		// get container id

		var optionalContainerId = dockerUtil().getContainerId();
		if (optionalContainerId.isEmpty()) {
			throw new MojoExecutionException("Container not found");
		}
		var containerId = optionalContainerId.get();

		// copy manifests to working directory

		var source = Path.of(kubectlManifests).toAbsolutePath();
		var destination = getWorkdir().resolve("manifests").toAbsolutePath();
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

		getLog().info("[kubectl] " + kubectlCommand);
		var execId = dockerClient().execCreateCmd(containerId)
				.withCmd("/bin/sh", "-c", kubectlCommand)
				.withWorkingDir("/k3s/manifests")
				.withEnv(List.of("KUBECONFIG=/k3s/kubeconfig.yaml"))
				.withAttachStdout(true)
				.withAttachStderr(true)
				.exec().getId();

		var callback = new DockerLogCallback(getLog(), kubectlStreamLogs);
		dockerClient().execStartCmd(execId).exec(callback);
		Await.await(kubectlCommand).until(callback::isCompleted);

		var response = dockerClient().inspectExecCmd(execId).exec();
		if (response.getExitCodeLong() != 0) {
			throw new MojoExecutionException("kubectl returned exit code " + response.getExitCodeLong());
		}

		// wait for pods to be ready

		Await.await("k3s pods ready").until(kubernetes()::isPodsReady);
		getLog().debug("k3s pods ready");
	}
}
