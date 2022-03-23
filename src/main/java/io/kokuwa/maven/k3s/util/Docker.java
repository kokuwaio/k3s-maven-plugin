package io.kokuwa.maven.k3s.util;

import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.InspectVolumeResponse;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.api.model.PropagationMode;
import com.github.dockerjava.api.model.SELContext;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Docker {

	public static final String K3S_NAME = "k3s-maven-plugin";
	public static final String K3S_LABEL = "io.kokuwa.maven.k3s";
	public static final String POD_LABEL = "io.kubernetes.pod.uid";

	private final DockerClient client;

	public Docker() {
		var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
		var httpClient = new ZerodepDockerHttpClient.Builder().dockerHost(config.getDockerHost()).build();
		client = DockerClientImpl.getInstance(config, httpClient);
	}

	public List<Container> listContainers() {
		return client.listContainersCmd().exec();
	}

	public List<InspectVolumeResponse> listVolumes() {
		return client.listVolumesCmd().exec().getVolumes();
	}

	public List<Network> listNetworks() {
		return client.listNetworksCmd().exec();
	}

	public Optional<Container> getK3sContainer() {
		return client.listContainersCmd().withShowAll(true).withLabelFilter(Set.of(K3S_LABEL)).exec().stream()
				.findFirst();
	}

	public List<Container> getPodContainers() {
		return client.listContainersCmd().withShowAll(true).withLabelFilter(Set.of(POD_LABEL)).exec();
	}

	public boolean isRunning(Container container) {
		var running = EnumSet.of(DockerState.RUNNING, DockerState.RESTARTING).contains(DockerState.valueOf(container));
		log.trace("Container '{}' is {}running (state: {})", container.getId(), running ? "" : "not ",
				container.getState());
		return running;
	}

	public Container createK3sContainer(String dockerImage, Path workingdir, List<String> command) {

		// host config (see https://github.com/rancher/k3d/issues/113)

		var hostConfig = new HostConfig()
				.withPrivileged(true)
				.withPidMode("host")
				.withBinds(
						new Bind(workingdir.toString(), new Volume("/k3s")),
						new Bind("/var/lib/docker", new Volume("/var/lib/docker"),
								AccessMode.DEFAULT, SELContext.DEFAULT, null, PropagationMode.RSHARED),
						new Bind("/var/lib/kubelet", new Volume("/var/lib/kubelet"),
								AccessMode.DEFAULT, SELContext.DEFAULT, null, PropagationMode.RSHARED),
						new Bind("/var/run/docker.sock", new Volume("/var/run/docker.sock")))
				.withPrivileged(true)
				.withNetworkMode("host");

		// container

		var container = client
				.createContainerCmd(dockerImage)
				.withName(Docker.K3S_NAME)
				.withCmd(command)
				.withLabels(Map.of(Docker.K3S_LABEL, Boolean.TRUE.toString()))
				.withHostConfig(hostConfig)
				.exec();
		log.debug("Container with id '{}' created, image: {}", container.getId(), dockerImage);

		return getK3sContainer().get();
	}

	public void startContainer(Container container) {
		client.startContainerCmd(container.getId()).exec();
		log.debug("Container with id '{}' and name '{}' started", container.getId(), container.getNames()[0]);
	}

	public void stopContainer(Container container) {
		if (isRunning(container)) {
			client.stopContainerCmd(container.getId()).exec();
			log.debug("Container with id '{}' and name '{}' stopped", container.getId(), container.getNames()[0]);
		}
	}

	public void removeContainer(Container container) {
		stopContainer(container);
		client.removeContainerCmd(container.getId()).withRemoveVolumes(true).withForce(true).exec();
		log.debug("Container with id '{}' and name '{}' removed", container.getId(), container.getNames()[0]);
	}

	public void logContainer(Container container, Instant since, DockerLogCallback callback) {
		client.logContainerCmd(container.getId())
				.withStdOut(true)
				.withStdErr(true)
				.withFollowStream(true)
				.withSince((int) since.getEpochSecond())
				.exec(callback);
	}

	public void exec(
			String message,
			Container container,
			Consumer<ExecCreateCmd> modifier,
			DockerLogCallback callback)
			throws MojoExecutionException {

		var cmd = client.execCreateCmd(container.getId())
				.withAttachStdout(true)
				.withAttachStderr(true);
		modifier.accept(cmd);
		var execId = cmd.exec().getId();

		client.execStartCmd(execId).exec(callback);
		Await.await("exec " + message).onTimeout(callback::replayOnWarn).until(callback::isCompleted);

		var response = client.inspectExecCmd(execId).exec();
		if (response.getExitCodeLong() != 0) {
			callback.replayOnWarn();
			throw new MojoExecutionException(message + " returned exit code " + response.getExitCodeLong());
		}
	}

	public boolean hasImage(String image) {
		var has = client.listImagesCmd().withImageNameFilter(image).exec().stream()
				.flatMap(i -> Optional.ofNullable(i.getRepoTags()).map(Stream::of).orElseGet(Stream::empty))
				.anyMatch(image::equals);
		log.debug("Image '{}' {}", image, has ? "already present" : "not found");
		return has;
	}

	public DockerPullCallback pullImage(String image) {
		return client.pullImageCmd(image).exec(new DockerPullCallback(log, image));
	}
}
