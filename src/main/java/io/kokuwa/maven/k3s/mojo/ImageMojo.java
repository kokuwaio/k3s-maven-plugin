package io.kokuwa.maven.k3s.mojo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.github.dockerjava.api.model.Container;

import io.kokuwa.maven.k3s.util.Await;
import lombok.Setter;

/**
 * Import images into k3s containerd.
 */
@Mojo(name = "image", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class ImageMojo extends K3sMojo {

	/** Download given images via `ctr image pull` inside k3s container. */
	@Setter @Parameter(property = "k3s.ctrImages")
	private List<String> ctrImages = new ArrayList<>();

	/** Import given tar files as images via `ctr image import` inside k3s container. */
	@Setter @Parameter(property = "k3s.tarFiles")
	private List<String> tarFiles = new ArrayList<>();

	/** Copy given images from docker deamon via `ctr image import` inside k3s container. */
	@Setter @Parameter(property = "k3s.dockerImages")
	private List<String> dockerImages = new ArrayList<>();

	/** Timout for `ctr image pull` or `docker pull` in seconds. */
	@Setter @Parameter(property = "k3s.pullTimeout", defaultValue = "1200")
	private int pullTimeout;

	/** Skip starting of k3s container. */
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

		// get container

		var containerOptional = docker.getContainer();
		if (containerOptional.isEmpty()) {
			throw new MojoExecutionException("No k3s container found");
		}
		var container = containerOptional.get();

		// get callables that handle images

		var existingImages = getCtrImages(container);
		var tasks = new HashSet<Callable<Boolean>>();
		dockerImages.forEach(requestedImage -> tasks.add(() -> docker(container, existingImages, requestedImage)));
		tarFiles.forEach(tarFile -> tasks.add(() -> tar(container, tarFile)));
		ctrImages.forEach(requestedImage -> tasks.add(() -> ctr(container, existingImages, requestedImage)));

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

	private boolean tar(Container container, String tar) {

		var sourcePath = Paths.get(tar).toAbsolutePath();
		if (!Files.isRegularFile(sourcePath)) {
			log.error("Tar not found: {}", sourcePath);
			return false;
		}

		var filename = sourcePath.hashCode() + ".tar";
		var targetPath = getImageDir().resolve(filename);
		try {
			Files.createDirectories(targetPath.getParent());
			Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
			docker.execThrows(container, "ctr image import /k3s/images/" + filename,
					Duration.ofMinutes(1));
		} catch (MojoExecutionException | IOException e) {
			log.error("Failed to import tar {}", filename, e);
			return false;
		}

		log.info("Imported tar from {}", sourcePath);
		return true;
	}

	private boolean ctr(Container container, List<String> existingImages, String image) {

		var normalizedImage = docker.normalizeDockerImage(image);
		if (existingImages.contains(normalizedImage)) {
			log.debug("Image {} found in ctr, skip pulling", image);
			return true;
		}

		log.info("Image {} not found, start pulling", image);
		try {
			docker.execThrows(container, "ctr image pull " + normalizedImage, Duration.ofSeconds(pullTimeout));
		} catch (MojoExecutionException e) {
			log.error("Failed to pull ctr image {}", image, e);
			return false;
		}

		log.info("Image {} pulled", image);
		return true;
	}

	private boolean docker(Container container, List<String> existingImages, String image) {

		if (existingImages.contains(image)) {
			log.debug("Image {} found in ctr, skip copy from docker", image);
			return true;
		}

		// pull image

		if (docker.findImage(image).isEmpty()) {
			log.debug("Image {} not found in docker, pulling ...", image);
			var pull = docker.pullImage(image);
			try {
				Await.await("docker pull image '" + image + "'")
						.timeout(Duration.ofSeconds(pullTimeout))
						.until(pull::isCompleted);
			} catch (MojoExecutionException e) {
				log.error("Failed to pull docker image {}", image, e);
				return false;
			}
			if (!pull.isSuccess()) {
				log.error("Failed to pull docker image {}: {}", image, pull.getResponse());
				return false;
			}
		} else {
			log.debug("Image {} found in docker", image);
		}

		// move from docker to ctr

		var filename = image.hashCode() + ".tar";
		var tarPath = getImageDir().resolve(filename);
		try {
			Files.createDirectories(getImageDir());
			docker.saveImage(image, tarPath);
			docker.execThrows(container, "ctr image import /k3s/images/" + filename, Duration.ofMinutes(1));
		} catch (MojoExecutionException | IOException e) {
			log.error("Failed to import tar {}", filename, e);
			return false;
		}

		log.info("Image {} copied from docker deamon", image);
		return true;
	}

	private List<String> getCtrImages(Container container) throws MojoExecutionException {
		var result = docker.execThrows(container, "ctr image list --quiet", Duration.ofSeconds(30));
		var output = result.getMessages().stream().collect(Collectors.joining());
		var images = List.of(output.split("\n"));
		if (log.isDebugEnabled()) {
			images.forEach(image -> log.debug("Found ctr image: {}", image));
		}
		return images;
	}

	private Path getImageDir() {
		return getMountDir().resolve("images");
	}
}
