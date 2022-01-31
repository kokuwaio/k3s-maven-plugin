package io.kokuwa.maven.k3s.mojo;

import java.io.IOException;
import java.nio.file.Files;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.kokuwa.maven.k3s.K3sMojo;
import lombok.Setter;

/**
 * Mojo to remove k3s docker container.
 */
@Mojo(name = "rm", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST, requiresProject = false)
public class RemoveMojo extends K3sMojo {

	/** Skip remove of k3s container. */
	@Setter @Parameter(property = "k3s.skipRm", defaultValue = "false")
	private boolean skipRm = false;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipRm)) {
			return;
		}

		// get container id

		var optionalContainerId = dockerUtil().getContainerId();
		if (optionalContainerId.isEmpty()) {
			getLog().debug("Container not found, skip remove");
			return;
		}
		var containerId = optionalContainerId.get();

		// stop container

		if (dockerUtil().isRunning(containerId)) {
			dockerClient().killContainerCmd(containerId).exec();
			getLog().info("Container with id '" + containerId + "' killed");
		}

		// remove container

		dockerClient().removeContainerCmd(containerId).exec();
		getLog().info("Container with id '" + containerId + "' removed");

		// remove obsolete config

		try {
			Files.deleteIfExists(getKubeConfig());
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to delete kubeconfig " + getKubeConfig(), e);
		}

		reset();
	}
}
