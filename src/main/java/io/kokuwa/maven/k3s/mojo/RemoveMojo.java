package io.kokuwa.maven.k3s.mojo;

import java.io.IOException;
import java.nio.file.Files;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import io.kokuwa.maven.k3s.K3sMojo;

/**
 * Mojo to remove k3s docker container.
 */
@Mojo(name = "rm", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST, requiresProject = false)
public class RemoveMojo extends K3sMojo {

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip()) {
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
			Files.deleteIfExists(getKubeconfig());
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to delete kubeconfi " + getKubeconfig(), e);
		}

		reset();
	}
}
