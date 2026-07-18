package io.kokuwa.maven.k3s.mojo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.Adler32;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.github.dockerjava.api.model.Container;
import io.kokuwa.maven.k3s.util.CtrImage;
import io.kokuwa.maven.k3s.util.Image;

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
	private List<Image> ctrImages = new ArrayList<>();

	/**
	 * Import given tar files as images via "ctr image import" inside k3s container.
	 *
	 * @since 0.3.0
	 */
	@Parameter(property = "k3s.tarFiles")
	private List<Path> tarFiles = new ArrayList<>();

	/**
	 * Copy given images from docker deamon via "ctr image import" inside k3s container.
	 *
	 * @since 0.3.0
	 */
	@Parameter(property = "k3s.dockerImages")
	private List<Image> dockerImages = new ArrayList<>();

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
		var existingImages = getDocker().getCtrImages(container);
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

	private boolean tar(Container container, List<CtrImage> existingImages, Path tarFile) {
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
			var oldChecksum = existingImages.stream().map(CtrImage::labels)
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

			var destination = "/tmp/" + tarFile.hashCode();
			getDocker().copyToContainer(container, tarFile, destination);
			var ctrImportOutput = getDocker().exec(container, "ctr", "image", "import",
					destination + "/" + tarFile.getFileName());

			// try to store checksum to avoid reimporting tar

			var outputPattern = Pattern.compile(".*(?<digest>sha256:[0-9a-f]{64}).*");
			var digest = ctrImportOutput.stream()
					.map(o -> outputPattern.matcher(o))
					.filter(m -> m.matches())
					.map(m -> m.group("digest")).findFirst().orElse(null);
			if (digest == null) {
				log.warn("Tar {} failed to determine image digest after import, checksum not stored.", tarFile);
				log.warn("Tar {} output was: \n{}", tarFile,
						ctrImportOutput.stream().collect(Collectors.joining("\n")));
				log.warn("Tar {} this is NOT a problem, but tar file will be imported every invocation", tarFile);
			} else {
				log.debug("Tar {} has digest {}", tarFile, digest);
				var images = getDocker().getCtrImages(container);
				var ref = CtrImage.findByDigest(images, digest).map(CtrImage::ref).orElse(null);
				if (ref == null) {
					log.warn("Tar {} has digest {} but no ref was found", tarFile, digest);
					log.warn("Tar {} this is NOT a problem, but tar file will be imported every invocation", tarFile);
				} else {
					getDocker().exec(container, "ctr", "image", "label", ref, labelPath + "=" + tarFile.toString());
					getDocker().exec(container, "ctr", "image", "label", ref, labelChecksum + "=" + newChecksum);
					log.debug("Tar {} labels stored", tarFile);
					log.info("Imported tar from {} as {}", tarFile, ref);
				}
			}

		} catch (MojoExecutionException | IOException e) {
			log.error("Failed to import tar: {}", tarFile, e);
			return false;
		}

		return true;
	}

	private boolean ctr(Container container, List<CtrImage> existingImages, Image image)
			throws MojoExecutionException {
		if (CtrImage.findByName(existingImages, image).isPresent()) {
			log.debug("Image {} found in ctr, skip pulling", image);
			return true;
		}

		log.info("Image {} not found, start pulling", image);
		// use crictl instead of cri, because crictl honors custom registry.yaml
		// see https://github.com/k3s-io/k3s/issues/5277
		getDocker().exec(container, pullTimeout, "crictl", "pull", image.toString());
		log.info("Image {} pulled", image);

		return true;
	}

	private boolean docker(Container container, List<CtrImage> existingImages, Image image) {
		// pull image

		var digest = getDocker().findImage(image).map(Image::digest).orElse(null);
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
			digest = getDocker().findImage(image).map(Image::digest).orElse(null);
			log.debug("Image {} pull in docker with digest {}", image, digest);
		} else {
			log.debug("Image {} found in docker with digest {}", image, digest);
		}

		// skip if image is already present in ctr

		var ref = image.registry() + "/" + image.repository() + (image.tag() == null ? "" : ":" + image.tag());
		var label = "k3s-maven-digest";
		var oldDigest = existingImages.stream()
				.filter(i -> i.ref().equals(ref))
				.map(i -> i.labels().get(label))
				.findAny().orElse(null);
		if (oldDigest == null) {
			log.debug("Image {} does not exists in ctr.", image);
		} else if (List.of(digest).contains(oldDigest)) {
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
			getDocker().exec(container, "ctr", "image", "import", "--digests", "--base-name=" + ref,
					"/tmp/" + filename);
			getDocker().exec(container, "ctr", "image", "label", image.toString(), label + "=" + digest);
		} catch (MojoExecutionException e) {
			log.error("Failed to import tar {}", source, e);
			return false;
		}

		log.info("Image {} copied from docker deamon", image);
		return true;
	}

	// setter

	public void setCtrImages(List<String> ctrImages) {
		this.ctrImages = ctrImages.stream().map(Image::of).toList();
	}

	public void setDockerImages(List<String> dockerImages) {
		this.dockerImages = dockerImages.stream().map(Image::of).toList();
	}

	public void setTarFiles(List<String> tarFiles) {
		this.tarFiles = tarFiles.stream().map(Path::of).map(Path::toAbsolutePath).toList();
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
