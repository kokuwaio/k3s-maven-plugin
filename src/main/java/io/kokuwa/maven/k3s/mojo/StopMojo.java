package io.kokuwa.maven.k3s.mojo;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import lombok.Setter;

/**
 * Mojo for stopping k3s container.
 */
@Mojo(name = "stop", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST, requiresProject = false)
public class StopMojo extends K3sMojo {

	/** Skip stoppping of k3s container. */
	@Setter @Parameter(property = "k3s.skipStop", defaultValue = "false")
	private boolean skipStop;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipStop)) {
			return;
		}

		// stop container

		docker.getContainer().ifPresent(docker::stopContainer);
	}
}
