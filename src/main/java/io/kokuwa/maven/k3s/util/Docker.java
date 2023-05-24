package io.kokuwa.maven.k3s.util;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.InspectVolumeResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

public class Docker {

	public static final String K3S_LABEL = "io.kokuwa.maven.k3s";

	private final String containerName;
	private final String volumeName;
	private final Log log;
	private final DockerClient client;

	public Docker(String containerName, String volumeName, Log log) {
		var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
		var httpClient = new ZerodepDockerHttpClient.Builder().dockerHost(config.getDockerHost()).build();
		this.client = DockerClientImpl.getInstance(config, httpClient);
		this.containerName = containerName;
		this.volumeName = volumeName;
		this.log = log;
	}

	// images

	public String normalizeDockerImage(String image) {

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

		return newImageName;
	}

	public Optional<Image> findImage(String image) {
		var normalizeImage = normalizeDockerImage(image);
		return client.listImagesCmd()
				.withImageNameFilter(normalizeImage)
				.exec().stream()
				.filter(i -> Optional.ofNullable(i.getRepoTags())
						.map(Stream::of).orElseGet(Stream::empty)
						.map(this::normalizeDockerImage)
						.anyMatch(normalizeImage::equals))
				.findFirst();
	}

	public DockerPullCallback pullImage(String image) {
		return client.pullImageCmd(image).exec(new DockerPullCallback(log, image));
	}

	public void saveImage(String image, Path target) throws IOException {
		FileUtils.copyInputStreamToFile(client.saveImageCmd(image).exec(), target.toFile());
	}

	public void removeImage(String image) {
		findImage(image).map(Image::getId).ifPresent(id -> client.removeImageCmd(id).withForce(true).exec());
	}

	// other

	public List<Container> listContainers() {
		return client.listContainersCmd().exec();
	}

	public List<InspectVolumeResponse> listVolumes() {
		return client.listVolumesCmd().exec().getVolumes();
	}

	public List<Network> listNetworks() {
		return client.listNetworksCmd().exec();
	}

	public Optional<Container> getContainer() {
		return client.listContainersCmd().withShowAll(true)
				.withLabelFilter(Set.of(K3S_LABEL)).exec().stream()
				.findFirst();
	}

	public boolean isRunning(Container container) {
		var running = EnumSet.of(DockerState.RUNNING, DockerState.RESTARTING).contains(DockerState.valueOf(container));
		log.debug("Container '" + container.getId() + "' is " + (running ? "" : "not ") + " running (state: "
				+ container.getState() + ")");
		return running;
	}

	public Container createContainer(
			String dockerImage,
			Path mountDir,
			List<String> portBindings,
			List<String> command) {

		// host config

		var hostConfig = new HostConfig()
				.withPrivileged(true)
				.withBinds(List.of(new Bind(mountDir.toString(), new Volume("/k3s"))))
				.withMounts(List.of(new Mount()
						.withType(MountType.VOLUME)
						.withSource(volumeName)
						.withTarget("/var/lib/rancher/k3s/agent")))
				.withPortBindings(portBindings.stream().map(PortBinding::parse).collect(Collectors.toList()));

		// container

		var container = client
				.createContainerCmd(dockerImage)
				.withName(containerName)
				.withCmd(command)
				.withLabels(Map.of(Docker.K3S_LABEL, Boolean.TRUE.toString()))
				.withExposedPorts(List.copyOf(hostConfig.getPortBindings().getBindings().keySet()))
				.withHostConfig(hostConfig)
				.exec();
		log.debug("Container with id '" + container.getId() + "' created, image: " + dockerImage);

		return getContainer().get();
	}

	public void startContainer(Container container) {
		client.startContainerCmd(container.getId()).exec();
		log.debug("Container with id '" + container.getId() + "' and name '" + container.getNames()[0] + "' started");
	}

	public void stopContainer(Container container) {
		if (isRunning(container)) {
			client.stopContainerCmd(container.getId()).withTimeout(300).exec();
			log.debug(
					"Container with id '" + container.getId() + "' and name '" + container.getNames()[0] + "' stopped");
		}
	}

	public void removeContainer(Container container) {
		stopContainer(container);
		client.removeContainerCmd(container.getId()).withRemoveVolumes(true).withForce(true).exec();
		log.debug("Container with id '" + container.getId() + "' and name '" + container.getNames()[0] + "' removed");
	}

	public void logContainer(Container container, DockerLogCallback callback) {
		client.logContainerCmd(container.getId())
				.withStdOut(true)
				.withStdErr(true)
				.withFollowStream(true)
				.withSince(0)
				.exec(callback);
	}

	public ExecResult execThrows(Container container, String cmdString, Duration timeout)
			throws MojoExecutionException {
		log.debug("Execute command: " + cmdString);
		var callback = new DockerLogCallback(log);
		var result = exec(cmdString, container, cmd -> cmd.withCmd(cmdString.split(" ")), callback, timeout);
		if (result.getExitCode() != 0) {
			callback.replayOnWarn();
			throw new MojoExecutionException(cmdString + " returned exit code " + result.getExitCode());
		}
		return result;
	}

	public ExecResult exec(
			String message,
			Container container,
			Consumer<ExecCreateCmd> modifier,
			DockerLogCallback callback,
			Duration timeout)
			throws MojoExecutionException {

		var cmd = client.execCreateCmd(container.getId())
				.withAttachStdout(true)
				.withAttachStderr(true);
		modifier.accept(cmd);
		var execId = cmd.exec().getId();

		client.execStartCmd(execId).exec(callback);
		Await.await(log, "exec " + message).timeout(timeout).onTimeout(callback::replayOnWarn)
				.until(callback::isCompleted);

		var exitCode = client.inspectExecCmd(execId).exec().getExitCodeLong().intValue();
		var logs = callback.getMessages();
		return new ExecResult(exitCode, logs);
	}

	public boolean isVolumePresent() {
		try {
			return client.inspectVolumeCmd(volumeName).exec() != null;
		} catch (NotFoundException e) {
			return false;
		}
	}

	public void createVolume() {
		if (isVolumePresent()) {
			client.createVolumeCmd().withName(volumeName).exec();
			log.debug("Cache volume created");
		} else {
			log.debug("Reuse existing cache volume");
		}
	}

	public void removeVolume() {
		if (isVolumePresent()) {
			client.removeVolumeCmd(volumeName).exec();
			log.debug("Cache volume removed");
		} else {
			log.debug("Cache volume not found, skip removing");
		}
	}
}
