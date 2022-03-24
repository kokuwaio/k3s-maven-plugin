package io.kokuwa.maven.k3s.mojo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.kokuwa.maven.k3s.K3sMojo;
import io.kokuwa.maven.k3s.util.Await;
import io.kokuwa.maven.k3s.util.DockerPullCallback;
import lombok.Setter;

/**
 * Mojo to pull k3s image.
 */
@Mojo(name = "pull", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class PullMojo extends K3sMojo {

	/** Always pull images. */
	@Setter @Parameter(property = "k3s.pullAlways", defaultValue = "false")
	private boolean pullAlways;

	/** Pull images in seconds. */
	@Setter @Parameter(property = "k3s.pullTimeout", defaultValue = "300")
	private int pullTimeout;

	/** Additional images to pull. */
	@Setter @Parameter(property = "k3s.pullAdditionalImages")
	private List<String> pullAdditionalImages;

	/** Skip pull of k3s image. */
	@Setter @Parameter(property = "k3s.skipPull", defaultValue = "false")
	private boolean skipPull;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipPull)) {
			return;
		}

		// get list of images

		var images = new ArrayList<String>();
		images.add(getDockerImage());
		if (pullAdditionalImages != null) {
			images.addAll(pullAdditionalImages);
		}

		// check if image is alreay present

		if (!pullAlways) {
			images.removeIf(docker::hasImage);
		}

		// pull image

		var callbacks = images.stream().map(docker::pullImage).collect(Collectors.toSet());
		Await.await("pull images")
				.timeout(Duration.ofSeconds(pullTimeout))
				.until(() -> callbacks.stream().allMatch(DockerPullCallback::isCompleted));
		if (callbacks.stream().anyMatch(callback -> !callback.isSuccess())) {
			throw new MojoExecutionException("Failed to pull images");
		}
	}
}
