package io.kokuwa.maven.k3s.util;

import java.util.Optional;
import java.util.stream.Stream;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DockerUtil {

	public static final String CONTAINER_LABEL = "io.kokuwa.maven.k3s";
	public static final String CONTAINER_NAME = "k3s-maven-plugin";

	private final DockerClient client;

	public boolean isRunning(String containerId) {
		return isRunning(client.inspectContainerCmd(containerId).exec());
	}

	public boolean isRunning(InspectContainerResponse container) {
		return container.getState().getRunning()
				|| container.getState().getRestarting()
				|| container.getState().getPaused();
	}

	public Optional<String> getContainerId() {
		return client.listContainersCmd().withShowAll(true).exec().stream()
				.filter(container -> container.getLabels().containsKey(CONTAINER_LABEL))
				.filter(container -> Stream.of(container.getNames()).anyMatch(name -> name.contains(CONTAINER_NAME)))
				.map(Container::getId)
				.findFirst();
	}
}
