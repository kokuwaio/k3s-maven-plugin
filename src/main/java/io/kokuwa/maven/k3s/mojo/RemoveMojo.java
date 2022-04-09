package io.kokuwa.maven.k3s.mojo;

import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import lombok.Setter;

/**
 * Mojo to remove k3s docker container.
 */
@Mojo(name = "rm", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST, requiresProject = false)
public class RemoveMojo extends K3sMojo {

	/** Delete not only docker container, delete also cached data. */
	@Setter @Parameter(property = "k3s.includeCache", defaultValue = "false")
	private boolean includeCache;

	/** Skip remove of k3s container. */
	@Setter @Parameter(property = "k3s.skipRm", defaultValue = "false")
	private boolean skipRm;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipRm)) {
			return;
		}

		// remove containers

		docker.getContainer().ifPresent(docker::removeContainer);

		// remove obsolete config mounted to container

		var directory = includeCache ? getCacheDir() : getMountDir();
		log.debug("Remove directory: {}", directory);
		try {
			if (Files.exists(directory)) {
				FileUtils.forceDelete(directory.toFile());
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to delete directory at " + directory, e);
		}
	}
}
