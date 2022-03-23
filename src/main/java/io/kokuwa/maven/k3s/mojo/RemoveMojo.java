package io.kokuwa.maven.k3s.mojo;

import java.io.IOException;
import java.nio.file.Files;

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

		// remove containers

		docker.getK3sContainer().ifPresent(docker::removeContainer);
		docker.getPodContainers().forEach(docker::removeContainer);

		// remove obsolete config

		try {
			if (Files.exists(getWorkDir())) {
				FileUtils.forceDelete(getWorkDir().toFile());
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to delete working directory at " + getWorkDir(), e);
		}
	}
}
