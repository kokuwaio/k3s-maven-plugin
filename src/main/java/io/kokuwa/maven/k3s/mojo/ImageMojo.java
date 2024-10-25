package io.kokuwa.maven.k3s.mojo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Adler32;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.kokuwa.maven.k3s.util.Docker.ContainerImage;

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
	 * Timeout for "docker cp" in seconds.
	 *
	 * @since 1.5.0
	 */
	@Parameter(property = "k3s.copyToContainerTimeout", defaultValue = "120")
	private Duration copyTimeout;

	/**
	 * Timeout for "docker save" in seconds.
	 *
	 * @since 1.5.0
	 */
	@Parameter(property = "k3s.saveTimeout", defaultValue = "120")
	private Duration saveTimeout;

	/**
	 * Skip starting of k3s container.
	 *
	 * @since 0.3.0
	 */
	@Parameter(property = "k3s.skipImage", defaultValue = "false")
	private boolean skipImage;

	@Override
	public void execute() throws MojoExecutionException {

		// skip if no image is request

		if (isSkip(skipImage) || ctrImages.isEmpty() && tarFiles.isEmpty() && dockerImages.isEmpty()) {
			return;
		}

		// verify container

		if (getDocker().getContainer().isEmpty()) {
			throw new MojoExecutionException("No k3s container found");
		}

		// get callables that handle images

		var existingImages = getCtrImages();
		var tasks = new HashSet<Callable<Boolean>>();
		dockerImages.forEach(requestedImage -> tasks.add(() -> docker(existingImages, requestedImage)));
		tarFiles.forEach(tarFile -> tasks.add(() -> tar(existingImages, tarFile)));
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

	private boolean tar(Map<String, Map<String, ?>> existingImages, Path tarFile) {

		if (!Files.isRegularFile(tarFile)) {
			log.error("Tar not found: {}", tarFile);
			return false;
		}

		try {

			// skip if image is already present in ctr

			var labelPath = "k3s-maven-tar-path";
			var labelChecksum = "k3s-maven-tar-checksum";
			var checksum = new Adler32();
			checksum.update(Files.readAllBytes(tarFile));
			var newChecksum = "adler32:" + checksum.getValue();
			var oldChecksum = existingImages.values().stream()
					.filter(l -> Optional.ofNullable(l.get(labelPath)).map(tarFile.toString()::equals).orElse(false))
					.map(l -> l.get(labelChecksum)).filter(Objects::nonNull)
					.findAny().orElse(null);
			if (oldChecksum == null) {
				log.debug("Tar {} does not exists in ctr.", tarFile);
			} else if (oldChecksum.equals(newChecksum)) {
				log.info("Tar {} present in ctr with checksum {}, skip.", tarFile, newChecksum);
				return true;
			} else {
				log.debug("Tar {} present in ctr with checksum {}, new is: {}", tarFile, oldChecksum, newChecksum);
			}

			// import tar into ctr

			var destination = "/tmp/" + tarFile.getFileName() + "_" + System.nanoTime();
			var outputPattern = Pattern.compile("^unpacking (?<image>.*) \\(sha256:[0-9a-f]{64}\\).*$");

			getDocker().copyToContainer(tarFile, destination, copyTimeout);
			for (var output : getDocker().exec(pullTimeout, "ctr", "image", "import", destination.toString())) {
				var matcher = outputPattern.matcher(output);
				if (matcher.matches()) {
					getDocker().exec("ctr", "image", "label", matcher.group("image"), labelPath + "=" + tarFile);
					getDocker().exec("ctr", "image", "label", matcher.group("image"),
							labelChecksum + "=" + newChecksum);
				} else {
					log.warn("Tar {} import output cannot be parsed: {}", tarFile, output);
				}
			}
		} catch (MojoExecutionException | IOException e) {
			log.error("Failed to import tar: {}", tarFile, e);
			return false;
		}

		log.info("Imported tar from {}", tarFile);
		return true;
	}

	private boolean ctr(Map<String, Map<String, ?>> existingImages, String image) throws MojoExecutionException {

		if (existingImages.containsKey(image)) {
			log.debug("Image {} found in ctr, skip pulling", image);
			return true;
		}

		log.info("Image {} not found, start pulling", image);
		// use crictl instead of cri, because crictl honors custom registry.yaml
		// see https://github.com/k3s-io/k3s/issues/5277
		getDocker().exec(pullTimeout, "crictl", "pull", image);
		log.info("Image {} pulled", image);

		return true;
	}

	private boolean docker(Map<String, Map<String, ?>> existingImages, String image) throws MojoExecutionException {

		// pull image

		var digest = getDocker().getImage(image).map(ContainerImage::getDigest).orElse(null);
		if (dockerPullAlways || digest == null) {
			if (digest != null) {
				log.debug("Image {} found in docker, pull always ...", image);
			} else {
				log.debug("Image {} not found in docker, pulling ...", image);
			}
			try {
				getDocker().pullImage(image, pullTimeout);
			} catch (MojoExecutionException e) {
				log.error("Failed to pull docker image {}", image, e);
				return false;
			}
			digest = getDocker().getImage(image).map(ContainerImage::getDigest).orElse(null);
		} else {
			log.debug("Image {} found in docker", image);
		}

		// skip if image is already present in ctr

		var normalizedImage = getDocker().normalizeImage(image);
		var label = "k3s-maven-digest";
		var oldDigest = existingImages.getOrDefault(normalizedImage, Map.of()).get(label);
		if (oldDigest == null) {
			log.debug("Image {} does not exists in ctr.", image);
		} else if (oldDigest.equals(digest)) {
			log.info("Image {} present in ctr with digest {}, skip.", image, digest);
			return true;
		} else {
			log.debug("Image {} present in ctr with digest {}, new digest is: {}", image, oldDigest, digest);
		}

		// move from docker to ctr

		var filename = Paths.get(image.hashCode() + ".tar");
		var source = Paths.get(System.getProperty("java.io.tmpdir")).resolve(filename);
		var destination = "/tmp/" + filename;
		try {
			getDocker().saveImage(image, source, saveTimeout);
			getDocker().copyToContainer(source, destination, copyTimeout);
			getDocker().exec(pullTimeout, "ctr", "image", "import", destination.toString());
			getDocker().exec("ctr", "image", "label", normalizedImage, label + "=" + digest);
		} catch (MojoExecutionException e) {
			log.error("Failed to import tar {}", source, e);
			return false;
		}

		log.info("Image {} copied from docker deamon", image);
		return true;
	}

	/**
	 * Read image from <code>ctr image list</code>.
	 *
	 * @return Image name with labels.
	 */
	private Map<String, Map<String, ?>> getCtrImages() throws MojoExecutionException {
		return getDocker().exec("ctr", "image", "list").stream().map(String::strip)
				.filter(row -> !row.startsWith("REF") && !row.startsWith("sha256:"))
				.map(row -> row.split("(\\s)+"))
				.filter(parts -> {
					var matches = parts.length == 7;
					if (!matches) {
						log.warn("Unexpected output of `ctr image list`: {}", List.of(parts));
					}
					return matches;
				})
				.map(parts -> Map.entry(parts[0], Stream.of(parts[6].split(",")).map(s -> s.split("="))
						.filter(s -> !"io.cri-containerd.image".equals(s[0]))
						.collect(Collectors.toMap(s -> s[0], s -> s[1]))))
				.peek(entry -> log.debug("Found ctr image: {}", entry))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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

	public void setCopyTimeout(int copyTimeout) {
		this.copyTimeout = Duration.ofSeconds(copyTimeout);
	}

	public void setSaveTimeout(int saveTimeout) {
		this.saveTimeout = Duration.ofSeconds(saveTimeout);
	}

	public void setSkipImage(boolean skipImage) {
		this.skipImage = skipImage;
	}
}
