package io.kokuwa.maven.k3s.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

/**
 * Wrapper for docker commands.
 *
 * @author stephan@schnabel.org
 * @since 1.0.0
 */
@SuppressWarnings("resource")
public class Docker {

	private static final Logger log = LoggerFactory.getLogger(Docker.class);

	private final DockerClient client;
	private final String containerName;
	private final String volumeName;

	public Docker(String volumeName, String containerName) {
		var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
		var httpClient = new ZerodepDockerHttpClient.Builder().dockerHost(config.getDockerHost()).build();
		this.client = DockerClientImpl.getInstance(config, httpClient);
		this.volumeName = volumeName;
		this.containerName = containerName;
	}

	public DockerClient getClient() {
		return client;
	}

	// volume

	public boolean isVolumePresent() {
		try {
			return client.inspectVolumeCmd(volumeName).exec() != null;
		} catch (NotFoundException e) {
			return false;
		}
	}

	public void createVolume() {
		if (isVolumePresent()) {
			log.trace("Cache volume {} found, skip creating", volumeName);
		} else {
			client.createVolumeCmd().withName(volumeName).exec();
			log.debug("Cache volume {} created", volumeName);
		}
	}

	public void removeVolume() {
		if (isVolumePresent()) {
			client.removeVolumeCmd(volumeName).exec();
			log.debug("Volume {} removed", volumeName);
		} else {
			log.trace("Volume {} not found, skip removing", volumeName);
		}
	}

	// images

	public Optional<Image> findImage(Image image) {
		var digest = image.digest();
		var reference = (image.registry() + "/" + image.repository()).replace("library/", "").replace("docker.io/", "");
		var repoTagStart = reference
				+ (image.tag() == null ? (image.digest() == null ? ":latest" : "") : ":" + image.tag());

		log.trace("Search for docker images for {} using ref {}", image, reference);
		var images = client.listImagesCmd().withReferenceFilter(reference).exec();
		for (var i : images) {
			if (digest != null) {
				if (Stream.of(i.getRepoDigests()).noneMatch(d -> d.equals(reference + "@" + digest))) {
					log.trace("{} filtered because of repoDigest.", i);
					continue;
				}
			}
			var repoTag = Stream.of(i.getRepoTags()).filter(t -> t.startsWith(repoTagStart)).findAny().orElse(null);
			if (repoTag == null) {
				log.trace("{} filtered because of repoTag.", i);
				continue;
			}
			if (!repoTag.contains("@")) {
				repoTag += "@" + i.getId();
			}
			log.trace("{} found with name {}.", i, Image.of(repoTag));
			return Optional.of(Image.of(repoTag));
		}

		log.trace("Image {} not found.", image);
		return Optional.empty();
	}

	public void pullImage(Image image, Duration timeout) throws MojoExecutionException {
		var callback = client.pullImageCmd(image.toString()).exec(new DockerPullCallback(image));
		Await.await(log, "pull images").timeout(timeout).until(callback::isCompleted);
		if (!callback.isSuccess()) {
			throw new MojoExecutionException("Failed to pull image " + image);
		}
	}

	public void saveImage(Image image, Path target) throws MojoExecutionException {
		try {
			FileUtils.copyInputStreamToFile(client.saveImageCmd(image.toString()).exec(), target.toFile());
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to write image to file", e);
		}
	}

	public void removeImage(Image image) {
		client.listImagesCmd()
				.withReferenceFilter(
						(image.registry() + "/" + image.repository()).replace("library/", "").replace("docker.io/", ""))
				.exec()
				.forEach(i -> client.removeImageCmd(i.getId()).withForce(true).exec());
	}

	// container

	public Optional<Container> getContainer() {
		return client.listContainersCmd()
				.withShowAll(true)
				.withNameFilter(Set.of(containerName))
				.exec().stream().findFirst();
	}

	public Container createContainer(
			Image dockerImage,
			Path registries,
			List<String> portBindings,
			List<String> command) {
		// host config

		var mounts = new ArrayList<Mount>();
		mounts.add(new Mount()
				.withType(MountType.VOLUME)
				.withSource(volumeName)
				.withTarget("/var/lib/rancher/k3s/agent"));
		if (registries != null) {
			mounts.add(new Mount()
					.withType(MountType.BIND)
					.withReadOnly(true)
					.withSource(registries.toAbsolutePath().toString())
					.withTarget("/etc/rancher/k3s/registries.yaml"));
		}
		var hostConfig = new HostConfig()
				.withPrivileged(true)
				.withMounts(mounts)
				.withPortBindings(portBindings.stream().map(PortBinding::parse).toList());

		// container

		var container = client
				.createContainerCmd(dockerImage.toString())
				.withName(containerName)
				.withCmd(command)
				.withExposedPorts(List.copyOf(hostConfig.getPortBindings().getBindings().keySet()))
				.withHostConfig(hostConfig)
				.exec();
		log.debug("Container created with id {}", container.getId());
		for (var warning : container.getWarnings()) {
			log.warn("Container with id {} had warning: {}", container.getId(), warning);
		}

		return client.listContainersCmd()
				.withShowAll(true)
				.withIdFilter(Set.of(container.getId()))
				.exec().get(0);
	}

	public void remove(Container container) {
		client.removeContainerCmd(container.getId()).withRemoveVolumes(true).withForce(true).exec();
		log.debug("Container with id {} and name {} removed", container.getId(), container.getNames()[0]);
	}

	public void start(Container container) {
		client.startContainerCmd(container.getId()).exec();
		log.debug("Container {} and name {} started", container.getId(), container.getNames()[0]);
	}

	public void kill(Container container) throws MojoExecutionException {
		client.killContainerCmd(container.getId()).withSignal("SIGKILL").exec();
		log.debug("Container {} and name {} stopped", container.getId(), container.getNames()[0]);
		Await.await(log, "wait to kill container").until(() -> getContainer().get().getState().equals("exited"));
	}

	public boolean isRunning(Container container) {
		var state = container.getState();
		var running = "running".equals(state) || "restarting".equals(state);
		log.debug("Container {} is {}running (state: {})", container.getId(), running ? "" : "not ", state);
		return running;
	}

	public void waitForLog(Container container, Await await, Function<List<String>, Boolean> checker)
			throws MojoExecutionException {
		var callback = new DockerLogCallback();
		client.logContainerCmd(container.getId())
				.withStdOut(true)
				.withStdErr(true)
				.withFollowStream(true)
				.withSince(0)
				.exec(callback);
		await.onTimeout(callback::replayOnWarn).until(() -> checker.apply(callback.messages));
	}

	public void copyFromContainer(Container container, String source, Path destination) throws MojoExecutionException {
		log.debug("Copy from container {} to host {}", source, destination);
		try (var is = new TarArchiveInputStream(client.copyArchiveFromContainerCmd(container.getId(), source).exec())) {
			ArchiveEntry entry = null;
			while ((entry = is.getNextEntry()) != null) {
				var extractTo = destination.resolve(entry.getName());
				if (entry.isDirectory()) {
					Files.createDirectories(extractTo);
				} else {
					Files.copy(is, extractTo, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to copy file " + source + " to " + destination, e);
		}
	}

	public void copyToContainer(Container container, Path source, String destination) throws MojoExecutionException {
		log.debug("Copy from host {} to container {}", source, destination);
		exec(container, "mkdir", "-p", destination.toString());
		client.copyArchiveToContainerCmd(container.getId())
				.withHostResource(source.toAbsolutePath().toString())
				.withRemotePath(destination)
				.withDirChildrenOnly(true)
				.exec();
	}

	public List<String> exec(Container container, String... command) throws MojoExecutionException {
		return exec(container, null, command);
	}

	public List<String> exec(Container container, Duration timeout, String... command) throws MojoExecutionException {
		return execWithoutVerify(container, timeout, command).verify();
	}

	public DockerExecResult execWithoutVerify(Container container, Duration timeout, String... command)
			throws MojoExecutionException {
		var execId = client.execCreateCmd(container.getId())
				.withCmd(command)
				.withAttachStdout(true)
				.withAttachStderr(true)
				.exec().getId();
		var logs = client.execStartCmd(execId).exec(new DockerLogCallback());
		Await.await(log, Stream.of(command).collect(Collectors.joining(" ")))
				.timeout(timeout == null ? Duration.ofMinutes(30) : timeout)
				.onTimeout(logs::replayOnWarn)
				.until(logs::isCompleted);
		var exitCode = client.inspectExecCmd(execId).exec().getExitCodeLong();
		return new DockerExecResult(log, command, exitCode, logs.messages);
	}

	/**
	 * Read image from <code>ctr image list</code>.
	 *
	 * @return Image name with labels.
	 */
	public List<CtrImage> getCtrImages(Container container) throws MojoExecutionException {
		return exec(container, "ctr", "image", "list").stream()
				.filter(row -> !row.startsWith("REF"))
				.map(row -> row.split("(\\s)+"))
				.filter(parts -> {
					var matches = parts.length == 7;
					if (!matches) {
						log.warn("Unexpected output of `ctr image list`: {}", List.of(parts));
					}
					return matches;
				})
				.map(parts -> new CtrImage(parts[0].split("@")[0], parts[2], parts[3] + " " + parts[4], Stream
						.of(parts[6].split(",")).map(s -> s.split("="))
						.collect(Collectors.toMap(s -> s[0], s -> s[1]))))
				.peek(entry -> log.debug("Found ctr image: {}", entry))
				.toList();
	}
}
