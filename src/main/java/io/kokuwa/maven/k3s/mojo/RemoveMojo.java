package io.kokuwa.maven.k3s.mojo;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import lombok.Setter;

/**
 * Mojo to remove k3s container.
 *
 * @since 0.1.0
 */
@Mojo(name = "rm", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST, requiresProject = false)
public class RemoveMojo extends K3sMojo {

	/**
	 * Delete not only docker container, delete also cached data.
	 *
	 * @since 0.3.0
	 */
	@Setter @Parameter(property = "k3s.includeCache", defaultValue = "false")
	private boolean includeCache;

	/**
	 * Skip remove of k3s container.
	 *
	 * @since 0.1.0
	 */
	@Setter @Parameter(property = "k3s.skipRm", defaultValue = "false")
	private boolean skipRm;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipRm)) {
			return;
		}

		getDocker().removeContainer();
		if (includeCache) {
			getDocker().removeVolume();
		}
	}
}
