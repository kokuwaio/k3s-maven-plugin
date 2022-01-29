package io.kokuwa.maven.k3s.mojo;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import io.kokuwa.maven.k3s.K3sMojo;

/**
 * Mojo for stopping k3s.
 */
@Mojo(name = "stop", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST, requiresProject = false)
public class StopMojo extends K3sMojo {

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip()) {
			return;
		}

		// get container id

		var optionalContainerId = dockerUtil().getContainerId();
		if (optionalContainerId.isEmpty()) {
			getLog().info("[k3s] Container not found, skip stop");
			return;
		}
		var containerId = optionalContainerId.get();

		// stop container

		if (dockerUtil().isRunning(containerId)) {
			dockerClient().stopContainerCmd(containerId).exec();
			getLog().info("[k3s] Container with id '" + containerId + "' stopped");
		}
	}
}
