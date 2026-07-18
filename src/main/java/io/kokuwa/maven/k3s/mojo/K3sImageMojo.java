package io.kokuwa.maven.k3s.mojo;

import java.time.Duration;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

import io.kokuwa.maven.k3s.util.Image;

/**
 * Base for Mojos that reference the k3s image.
 *
 * @author stephan@schnabel.org
 */
public abstract class K3sImageMojo extends K3sMojo {

	/**
	 * k3s image registry.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.imageRegistry")
	private String imageRegistry;

	/**
	 * k3s image repository.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.imageRepository", defaultValue = "docker.io/rancher/k3s")
	private String imageRepository;

	/**
	 * k3s image tag.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.imageTag", defaultValue = "latest")
	private String imageTag;

	Image getImage() {
		return Image.of((imageRegistry == null ? "" : imageRegistry + "/") + imageRepository + ":" + imageTag);
	}

	Image pullImage() throws MojoExecutionException {
		if ("latest".equals(imageTag)) {
			log.warn("Using image tag 'latest' is unstable.");
		}
		getDocker().pullImage(getImage(), Duration.ofMinutes(10));
		return getImage();
	}

	// setter

	public void setImageRegistry(String imageRegistry) {
		this.imageRegistry = imageRegistry;
	}

	public void setImageRepository(String imageRepository) {
		this.imageRepository = imageRepository;
	}

	public void setImageTag(String imageTag) {
		this.imageTag = imageTag;
	}
}
