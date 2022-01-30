package io.kokuwa.maven.k3s.mojo;

import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.kokuwa.maven.k3s.K3sMojo;
import lombok.Setter;

/**
 * Mojo to pull k3s image.
 */
@Mojo(name = "pull", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class PullMojo extends K3sMojo {

	@Setter @Parameter(property = "k3s.pull.always", defaultValue = "false")
	private boolean pullAlways = false;
	@Setter @Parameter(property = "k3s.pull.skip", defaultValue = "false")
	private boolean skipPull = false;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipPull)) {
			return;
		}

		// check if image is alreay present

		if (!pullAlways) {
			var imagePresent = dockerClient().listImagesCmd()
					.withImageNameFilter(dockerImage())
					.exec().stream()
					.flatMap(i -> Stream.of(i.getRepoTags()))
					.anyMatch(dockerImage()::equals);
			if (imagePresent) {
				getLog().debug("Image '" + dockerImage() + "' found, skip pull");
				return;
			}
		}

		// pull image

		try {
			getLog().info("Image '" + dockerImage() + "' not found, start pulling image ...");
			dockerClient().pullImageCmd(dockerImage()).start().awaitCompletion();
			getLog().info("Image '" + dockerImage() + "' pulled");
		} catch (InterruptedException e) {
			throw new MojoExecutionException("Failed to pull image " + dockerImage(), e);
		}
	}
}
