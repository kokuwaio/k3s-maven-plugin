package io.kokuwa.maven.k3s.mojo;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import lombok.Setter;

/**
 * Mojo for stopping k3s container.
 *
 * @since 0.1.0
 */
@Mojo(name = "stop", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST, requiresProject = false)
public class StopMojo extends K3sMojo {

	/**
	 * Skip stoppping of k3s container.
	 *
	 * @since 0.1.0
	 */
	@Setter @Parameter(property = "k3s.skipStop", defaultValue = "false")
	private boolean skipStop;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipStop)) {
			return;
		}

		// stop container

		getDocker().getContainer().ifPresent(getDocker()::stopContainer);
	}
}
