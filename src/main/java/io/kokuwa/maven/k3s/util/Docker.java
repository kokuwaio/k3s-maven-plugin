package io.kokuwa.maven.k3s.util;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
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

	public String normalizeImage(String image) {

		var newImageName = image;

		if (!image.contains("@sha256:") && !image.contains(":")) {
			newImageName += ":latest";
		}

		var slashIndex = image.indexOf('/');
		if (slashIndex == -1) {
			newImageName = "docker.io/library/" + newImageName;
		} else if (!image.substring(0, slashIndex).contains(".")) {
			newImageName = "docker.io/" + newImageName;
		}

		log.trace("Normalized {} to {}", image, newImageName);

		return newImageName;
	}

	public Optional<Image> findImage(String image) {
		var normalizedImage = normalizeImage(image);
		return client.listImagesCmd()
				.withImageNameFilter(normalizedImage)
				.exec().stream()
				.filter(i -> Optional.ofNullable(i.getRepoTags())
						.map(Stream::of).orElseGet(Stream::empty)
						.map(this::normalizeImage)
						.anyMatch(normalizedImage::equals))
				.findFirst();
	}

	public void pullImage(String image, Duration timeout) throws MojoExecutionException {
		var callback = client.pullImageCmd(image).exec(new DockerPullCallback(image));
		Await.await(log, "pull images").timeout(timeout).until(callback::isCompleted);
		if (!callback.isSuccess()) {
			throw new MojoExecutionException("Failed to pull image " + image);
		}
	}

	public void saveImage(String image, Path target) throws MojoExecutionException {
		try {
			FileUtils.copyInputStreamToFile(client.saveImageCmd(image).exec(), target.toFile());
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to write image to file", e);
		}
	}

	public void removeImage(String image) {
		findImage(image).map(Image::getId).ifPresent(id -> client.removeImageCmd(id).withForce(true).exec());
	}

	// container

	public Optional<Container> getContainer() {
		return client.listContainersCmd()
				.withShowAll(true)
				.withNameFilter(Set.of(containerName))
				.exec().stream().findFirst();
	}

	public Container createContainer(
			String dockerImage,
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
				.createContainerCmd(dockerImage)
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
		client.copyArchiveFromContainerCmd(container.getId(), source)
				.withHostPath(destination.toString())
				.exec();
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
}
