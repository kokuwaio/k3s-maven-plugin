package io.kokuwa.maven.k3s.mojo;

import java.util.Optional;
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

	/** Always pull k3s image. */
	@Setter @Parameter(property = "k3s.imagePullAlways", defaultValue = "false")
	private boolean imagePullAlways = false;

	/** Skip pull of k3s image. */
	@Setter @Parameter(property = "k3s.skipPull", defaultValue = "false")
	private boolean skipPull = false;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipPull)) {
			return;
		}

		// check if image is alreay present

		if (!imagePullAlways) {
			var imagePresent = dockerClient().listImagesCmd()
					.withImageNameFilter(dockerImage())
					.exec().stream()
					.flatMap(i -> Optional.ofNullable(i.getRepoTags()).map(Stream::of).orElseGet(Stream::empty))
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
