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

//	public void kubectl(String... command) throws MojoExecutionException {
//
//		// start container
//
//		var containerName = LABEL + "-kubectl";
//		var containerId = client
//				.createContainerCmd(image)
//				.withName(containerName)
//				.withEntrypoint("kubectl")
//				.withCmd(command)
//				.withEnv("KUBECONFIG=/k3s/kubeconfig.yaml")
//				.withHostConfig(new HostConfig()
//						.withBinds(new Bind(Paths.get("target/k3s/kubeconfig.yaml").toAbsolutePath().toString(),
//								new Volume("/k3s/kubeconfig.yaml"))))
//				.exec()
//				.getId();
//		log.debug("Container '" + containerName + "' with id '" + containerId + "' created");
//		client.startContainerCmd(containerId).exec();
//		log.debug("Container '" + containerName + "' with id '" + containerId + "' started");
//
//		// wait for container terminated
//
//		var inspectCmd = client.inspectContainerCmd(containerId);
//		var state = Await
//				.await("kubectl apply")
//				.until(() -> inspectCmd.exec().getState(), s -> !s.getRunning());
//		log.debug("Container '" + containerName + "' with id '" + containerId + "' finished");
//
//		// logs to console (and remove container after logging all frames)
//
//		client.logContainerCmd(containerId)
//				.withStdOut(true)
//				.withStdErr(true)
//				.withFollowStream(false)
//				.exec(new LogFrameCallback(log, containerName) {
//					@Override
//					public void onComplete() {
//						client.removeContainerCmd(containerId).withForce(true).exec();
//						log.debug("Container '" + containerName + "' with id '" + containerId + "' removed");
//					}
//				});
//
//		// validate state
//
//		if (state.getExitCodeLong() != 0) {
//			throw new MojoExecutionException("kubectl returned status code " + state.getExitCodeLong());
//		}
//	}
}
