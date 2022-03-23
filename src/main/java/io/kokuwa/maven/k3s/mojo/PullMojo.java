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
	private boolean imagePullAlways;
	/** Skip pull of k3s image. */
	@Setter @Parameter(property = "k3s.skipPull", defaultValue = "false")
	private boolean skipPull;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipPull)) {
			return;
		}

		// check if image is alreay present

		var dockerImage = getDockerImage();
		if (!imagePullAlways) {
			var imagePresent = docker.client().listImagesCmd()
					.withImageNameFilter(dockerImage)
					.exec().stream()
					.flatMap(i -> Optional.ofNullable(i.getRepoTags()).map(Stream::of).orElseGet(Stream::empty))
					.anyMatch(dockerImage::equals);
			if (imagePresent) {
				log.debug("Image '{}' found, skip pull", dockerImage);
				return;
			}
		}

		// pull image

		try {
			log.info("Image '{}' not found, start pulling image ...", dockerImage);
			docker.client().pullImageCmd(dockerImage).start().awaitCompletion();
			log.info("Image '{}' pulled", dockerImage);
		} catch (InterruptedException e) {
			throw new MojoExecutionException("Failed to pull image " + dockerImage, e);
		}
	}
}
