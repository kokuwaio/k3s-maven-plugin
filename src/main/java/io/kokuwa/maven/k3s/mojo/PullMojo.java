package io.kokuwa.maven.k3s.mojo;

import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import io.kokuwa.maven.k3s.K3sMojo;

/**
 * Mojo to pull k3s image.
 */
@Mojo(name = "pull", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class PullMojo extends K3sMojo {

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip()) {
			return;
		}

		// check if image is alreay present

		if (!isPullAlways()) {
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
