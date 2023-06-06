package io.kokuwa.maven.k3s.mojo;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import lombok.Setter;

/**
 * Import images into k3s containerd.
 *
 * @since 0.3.0
 */
@Mojo(name = "image", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class ImageMojo extends K3sMojo {

	/**
	 * Download given images via "ctr image pull" inside k3s container.
	 *
	 * @since 0.3.0
	 */
	@Setter @Parameter(property = "k3s.ctrImages")
	private List<String> ctrImages = new ArrayList<>();

	/**
	 * Import given tar files as images via "ctr image import" inside k3s container.
	 *
	 * @since 0.3.0
	 */
	@Setter @Parameter(property = "k3s.tarFiles")
	private List<String> tarFiles = new ArrayList<>();

	/**
	 * Copy given images from docker deamon via "ctr image import" inside k3s container.
	 *
	 * @since 0.3.0
	 */
	@Setter @Parameter(property = "k3s.dockerImages")
	private List<String> dockerImages = new ArrayList<>();

	/**
	 * Always pull docker images or only if not present.
	 *
	 * @since 0.10.0
	 */
	@Setter @Parameter(property = "k3s.dockerPullAlways", defaultValue = "false")
	private boolean dockerPullAlways;

	/**
	 * Timout for "ctr image pull" or "docker pull" in seconds.
	 *
	 * @since 0.4.0
	 */
	@Setter @Parameter(property = "k3s.pullTimeout", defaultValue = "1200")
	private int pullTimeout;

	/**
	 * Skip starting of k3s container.
	 *
	 * @since 0.3.0
	 */
	@Setter @Parameter(property = "k3s.skipImage", defaultValue = "false")
	private boolean skipImage;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipImage)) {
			return;
		}

		// skip if no image is request

		if (ctrImages.isEmpty() && tarFiles.isEmpty() && dockerImages.isEmpty()) {
			return;
		}

		// verify container

		if (getDocker().getContainer().isEmpty()) {
			throw new MojoExecutionException("No k3s container found");
		}

		// get callables that handle images

		var existingImages = getCtrImages();
		var tasks = new HashSet<Callable<Boolean>>();
		dockerImages.forEach(requestedImage -> tasks.add(() -> docker(requestedImage)));
		tarFiles.forEach(tarFile -> tasks.add(() -> tar(tarFile)));
		ctrImages.forEach(requestedImage -> tasks.add(() -> ctr(existingImages, requestedImage)));

		// execute callables

		try {
			var success = true;
			for (var future : Executors.newWorkStealingPool().invokeAll(tasks)) {
				success &= future.get();
			}
			if (!success) {
				throw new MojoExecutionException("Failed to handle images, see previous log");
			}
		} catch (InterruptedException | ExecutionException e) {
			throw new MojoExecutionException("Failed to handle images", e);
		}
	}

	private boolean tar(String tar) {

		var source = Paths.get(tar).toAbsolutePath();
		if (!Files.isRegularFile(source)) {
			getLog().error("Tar not found: " + source);
			return false;
		}

		var destination = Paths.get("/tmp").resolve(source.getFileName());
		try {
			getDocker().copyToContainer(source, destination);
			getDocker().exec(Duration.ofSeconds(pullTimeout), "ctr", "image", "import", destination.toString());
		} catch (MojoExecutionException e) {
			getLog().error("Failed to import tar: " + source, e);
			return false;
		}

		getLog().info("Imported tar from " + source);
		return true;
	}

	private boolean ctr(List<String> existingImages, String image) throws MojoExecutionException {

		var normalizedImage = getDocker().normalizeImage(image);
		if (existingImages.contains(normalizedImage)) {
			getLog().debug("Image " + image + " found in ctr, skip pulling");
			return true;
		}

		getLog().info("Image " + image + " not found, start pulling");
		getDocker().exec(Duration.ofSeconds(pullTimeout), "ctr", "image", "pull", normalizedImage);
		getLog().info("Image " + image + " pulled");

		return true;
	}

	private boolean docker(String image) throws MojoExecutionException {

		// pull image

		var imagePresent = getDocker().getImage(image).isPresent();
		if (dockerPullAlways || !imagePresent) {
			if (imagePresent) {
				getLog().debug("Image " + image + " found in docker, pull always ...");
			} else {
				getLog().debug("Image " + image + " not found in docker, pulling ...");
			}
			try {
				getDocker().pullImage(image, Duration.ofSeconds(pullTimeout));
			} catch (MojoExecutionException e) {
				getLog().error("Failed to pull docker image " + image, e);
				return false;
			}
		} else {
			getLog().debug("Image " + image + " found in docker");
		}

		// move from docker to ctr

		var filename = Paths.get(image.hashCode() + ".tar");
		var source = Paths.get(System.getProperty("java.io.tmpdir")).resolve(filename);
		var destination = Paths.get("/tmp").resolve(filename);
		try {
			getDocker().saveImage(image, source);
			getDocker().copyToContainer(source, destination);
			getDocker().exec("ctr", "image", "import", destination.toString());
		} catch (MojoExecutionException e) {
			getLog().error("Failed to import tar " + source, e);
			return false;
		}

		getLog().info("Image " + image + " copied from docker deamon");
		return true;
	}

	private List<String> getCtrImages() throws MojoExecutionException {
		var images = getDocker().exec("ctr", "image", "list", "--quiet");
		images.forEach(image -> getLog().debug("Found ctr image: " + image));
		return images;
	}
}
