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
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.github.dockerjava.api.model.Container;

import io.kokuwa.maven.k3s.util.Await;
import io.kokuwa.maven.k3s.util.DockerPullCallback;
import lombok.Setter;
import lombok.SneakyThrows;

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

		// handle images

		tar(container, tarFiles);
		docker(container, dockerImages);
		ctr(container, ctrImages);
	}

	@SneakyThrows(IOException.class)
	private void tar(Container container, List<String> tars) throws MojoExecutionException {
		for (var tar : tars) {

			var sourcePath = Paths.get(tar).toAbsolutePath();
			if (!Files.isRegularFile(sourcePath)) {
				throw new MojoExecutionException("Tar not found: " + sourcePath);
			}

			var targetPath = getImageDir().resolve(sourcePath.getFileName());
			Files.createDirectories(targetPath.getParent());
			Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
			docker.execThrows(container, "ctr image import /k3s/images/" + sourcePath.getFileName(),
					Duration.ofSeconds(300));
			log.info("Imported tar from {}", sourcePath);
		}
	}

	private void ctr(Container container, List<String> images) throws MojoExecutionException {

		if (images.isEmpty()) {
			return;
		}

		var existingImages = listImages(container);
		for (var requestedImage : images) {
			var image = docker.normalizeDockerImage(requestedImage);
			if (existingImages.contains(image)) {
				log.info("Image {} found, skip pulling", image);
			} else {
				log.info("Image {} not found, start pulling", image);
				docker.execThrows(container, "ctr image pull " + image, Duration.ofSeconds(pullTimeout));
				log.info("Image {} pulled", image);
			}
		}
	}

	private void docker(Container container, List<String> images) throws MojoExecutionException {

		if (images.isEmpty()) {
			return;
		}

		// pull missing images

		var pulls = new HashSet<DockerPullCallback>();
		for (var image : images) {
			if (docker.findImage(image).isEmpty()) {
				log.debug("Image {} not found in docker, pulling ...", image);
				pulls.add(docker.pullImage(image));
			} else {
				log.debug("Image {} found in docker", image);
			}
		}
		if (!pulls.isEmpty()) {
			Await.await("docker pull images")
					.timeout(Duration.ofSeconds(pullTimeout))
					.until(() -> pulls.stream().allMatch(DockerPullCallback::isCompleted));
			var failure = pulls.stream().filter(result -> !result.isSuccess()).findAny();
			if (failure.isPresent()) {
				throw new MojoExecutionException("Docker pull failed: " + failure.get().getResponse());
			}
		}

		// move from docker to ctr

		for (var image : images) {

			var filename = image.hashCode() + ".tar";
			var tarPath = getImageDir().resolve(filename);
			try {
				Files.createDirectories(getImageDir());
				docker.saveImage(image, tarPath);
			} catch (IOException e) {
				throw new MojoExecutionException("Failed to write image to " + tarPath, e);
			}

			docker.execThrows(container, "ctr image import /k3s/images/" + filename, Duration.ofSeconds(300));
			log.info("Image {} copied from docker deamon", image);
		}
	}

	private List<String> listImages(Container container) throws MojoExecutionException {
		var result = docker.execThrows(container, "ctr image list --quiet", Duration.ofSeconds(30));
		var output = result.getMessages().stream().collect(Collectors.joining());
		return List.of(output.split("\n"));
	}

	private Path getImageDir() {
		return getMountDir().resolve("images");
	}
}
