package io.kokuwa.maven.k3s.mojo;

import java.io.IOException;

import org.apache.commons.io.FileUtils;
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
	private boolean skipRm;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipRm)) {
			return;
		}

		// get container id

		var optionalContainerId = dockerUtil().getContainerId();
		if (optionalContainerId.isEmpty()) {
			log.debug("Container not found, skip remove");
			return;
		}
		var containerId = optionalContainerId.get();

		// remove container with volumes

		dockerClient().removeContainerCmd(containerId).withRemoveVolumes(true).withForce(true).exec();
		log.info("Container with id '{}' removed", containerId);

		// remove spawned container

		for (var container : dockerClient().listContainersCmd().exec()) {
			if (container.getLabels().containsKey("io.kubernetes.pod.uid")) {
				dockerClient().removeContainerCmd(container.getId()).withRemoveVolumes(true).withForce(true).exec();
				log.debug("Container with id '{}' removed", containerId);
			}
		}

		// remove obsolete config

		try {
			FileUtils.forceDelete(getWorkingDir().toFile());
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to delete working " + getWorkingDir(), e);
		}

		resetKubernetes();
	}
}
