package io.kokuwa.maven.k3s.mojo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

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
	@Parameter(property = "k3s.ctrImages")
	private Set<String> ctrImages = new HashSet<>();

	/**
	 * Import given tar files as images via "ctr image import" inside k3s container.
	 *
	 * @since 0.3.0
	 */
	@Parameter(property = "k3s.tarFiles")
	private Set<Path> tarFiles = new HashSet<>();

	/**
	 * Copy given images from docker deamon via "ctr image import" inside k3s container.
	 *
	 * @since 0.3.0
	 */
	@Parameter(property = "k3s.dockerImages")
	private Set<String> dockerImages = new HashSet<>();

	/**
	 * Always pull docker images or only if not present.
	 *
	 * @since 0.10.0
	 */
	@Parameter(property = "k3s.dockerPullAlways", defaultValue = "false")
	private boolean dockerPullAlways;

	/**
	 * Timout for "ctr image pull" or "docker pull" in seconds.
	 *
	 * @since 0.4.0
	 */
	@Parameter(property = "k3s.pullTimeout", defaultValue = "1200")
	private Duration pullTimeout;

	/**
	 * Skip starting of k3s container.
	 *
	 * @since 0.3.0
	 */
	@Parameter(property = "k3s.skipImage", defaultValue = "false")
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

		var existingImages = getDocker().exec("ctr", "image", "list", "--quiet");
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

	private boolean tar(Path tarFile) {

		if (!Files.isRegularFile(tarFile)) {
			getLog().error("Tar not found: " + tarFile);
			return false;
		}

		var destination = Paths.get("/tmp").resolve(tarFile.getFileName() + "_" + System.nanoTime());
		try {
			getDocker().copyToContainer(tarFile, destination);
			getDocker().exec("ctr", "image", "import", destination.toString());
		} catch (MojoExecutionException e) {
			getLog().error("Failed to import tar: " + tarFile, e);
			return false;
		}

		getLog().info("Imported tar from " + tarFile);
		return true;
	}

	private boolean ctr(List<String> existingImages, String image) throws MojoExecutionException {

		if (existingImages.contains(image)) {
			getLog().debug("Image " + image + " found in ctr, skip pulling");
			return true;
		}

		getLog().info("Image " + image + " not found, start pulling");
		getDocker().exec(pullTimeout, "ctr", "image", "pull", image);
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
				getDocker().pullImage(image, pullTimeout);
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

	// setter

	public void setCtrImages(List<String> ctrImages) {
		this.ctrImages = ctrImages.stream().map(getDocker()::normalizeImage).collect(Collectors.toSet());
	}

	public void setDockerImages(List<String> dockerImages) {
		this.dockerImages = dockerImages.stream().collect(Collectors.toSet());
	}

	public void setTarFiles(List<String> tarFiles) {
		this.tarFiles = tarFiles.stream().map(Paths::get).map(Path::toAbsolutePath).collect(Collectors.toSet());
	}

	public void setDockerPullAlways(boolean dockerPullAlways) {
		this.dockerPullAlways = dockerPullAlways;
	}

	public void setPullTimeout(int pullTimeout) {
		this.pullTimeout = Duration.ofSeconds(pullTimeout);
	}

	public void setSkipImage(boolean skipImage) {
		this.skipImage = skipImage;
	}
}
