package io.kokuwa.maven.k3s.mojo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;

/**
 * Import images into k3s containerd.
 *
 * @author stephan@schnabel.org
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

		// skip if no image is request

		if (isSkip(skipImage) || ctrImages.isEmpty() && tarFiles.isEmpty() && dockerImages.isEmpty()) {
			return;
		}

		// get callables that handle images

		var container = getDocker().getContainer().orElseThrow(() -> new MojoExecutionException("No container found"));
		var existingImages = getCtrImages(container);
		var tasks = new HashSet<Callable<Boolean>>();
		dockerImages.forEach(requestedImage -> tasks.add(() -> docker(container, existingImages, requestedImage)));
		tarFiles.forEach(tarFile -> tasks.add(() -> tar(container, existingImages, tarFile)));
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

	private boolean tar(Container container, Map<String, Map<String, ?>> existingImages, Path tarFile) {

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

			var destination = "/tmp/" + System.nanoTime();
			var outputPattern = Pattern.compile("^unpacking (?<image>.*) \\(sha256:[0-9a-f]{64}\\).*$");
			getDocker().copyToContainer(container, tarFile, destination);
			for (var output : getDocker().exec(container, "ctr", "image", "import",
					destination + "/" + tarFile.getFileName())) {
				var matcher = outputPattern.matcher(output);
				if (matcher.matches()) {
					getDocker().exec(container, "ctr", "image", "label", matcher.group("image"),
							labelPath + "=" + tarFile);
					getDocker().exec(container, "ctr", "image", "label", matcher.group("image"),
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

	private boolean ctr(Container container, Map<String, Map<String, ?>> existingImages, String image)
			throws MojoExecutionException {

		if (existingImages.containsKey(image)) {
			log.debug("Image {} found in ctr, skip pulling", image);
			return true;
		}

		log.info("Image {} not found, start pulling", image);
		// use crictl instead of cri, because crictl honors custom registry.yaml
		// see https://github.com/k3s-io/k3s/issues/5277
		getDocker().exec(container, pullTimeout, "crictl", "pull", image);
		log.info("Image {} pulled", image);

		return true;
	}

	private boolean docker(Container container, Map<String, Map<String, ?>> existingImages, String image) {

		// pull image

		var digest = getDocker().findImage(image).map(Image::getRepoDigests).orElse(null);
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
			digest = getDocker().findImage(image).map(Image::getRepoDigests).orElse(null);
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

		var filename = Path.of(image.hashCode() + ".tar");
		var source = Path.of(System.getProperty("java.io.tmpdir")).resolve(filename);
		try {
			getDocker().saveImage(image, source);
			getDocker().copyToContainer(container, source, "/tmp/");
			getDocker().exec(container, "ctr", "image", "import", "/tmp/" + filename);
			getDocker().exec(container, "ctr", "image", "label", normalizedImage, label + "=" + digest);
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
	private Map<String, Map<String, ?>> getCtrImages(Container container) throws MojoExecutionException {
		return getDocker().exec(container, "ctr", "image", "list").stream().map(String::strip)
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
		this.dockerImages = Set.copyOf(dockerImages);
	}

	public void setTarFiles(List<String> tarFiles) {
		this.tarFiles = tarFiles.stream().map(Path::of).map(Path::toAbsolutePath).collect(Collectors.toSet());
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
