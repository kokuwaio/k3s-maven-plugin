package io.kokuwa.maven.k3s.mojo;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports.Binding;
import com.github.dockerjava.api.model.Volume;

import io.kokuwa.maven.k3s.K3sMojo;
import io.kokuwa.maven.k3s.util.Await;
import io.kokuwa.maven.k3s.util.DockerLogCallback;
import io.kokuwa.maven.k3s.util.DockerUtil;
import lombok.Setter;

/**
 * Mojo for create and start k3s.
 */
@Mojo(name = "start", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class StartMojo extends K3sMojo {

	/** Stream logs of `k3s` to maven logger. */
	@Setter @Parameter(property = "k3s.streamLogs", defaultValue = "false")
	private boolean streamLogs = false;

	/** Additional port bindings e.g. `8080:8080`. */
	@Setter @Parameter(property = "k3s.portBindings")
	private List<String> portBindings = new ArrayList<>();

	/** KubeApi port to expose to host. */
	@Setter @Parameter(property = "k3s.portKubeApi", defaultValue = "6443")
	private Integer portKubeApi = 6443;

	/** Timeout in seconds to wait for nodes getting ready. */
	@Setter @Parameter(property = "k3s.nodeTimeout", defaultValue = "60")
	private int nodeTimeout = 60;

	/** Timeout in seconds to wait for pods getting ready. */
	@Setter @Parameter(property = "k3s.podTimeout", defaultValue = "300")
	private int podTimeout = 300;

	/** Skip starting of k3s container. */
	@Setter @Parameter(property = "k3s.skipStart", defaultValue = "false")
	private boolean skipStart = false;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipStart)) {
			return;
		}

		var containerId = dockerUtil().getContainerId().orElse(null);
		var running = containerId != null && dockerUtil().isRunning(containerId);
		if (containerId != null) {
			if (!Files.exists(getKubeConfig()) && running) {
				running = false;
				dockerClient().stopContainerCmd(containerId).exec();
				getLog().info("Container with id '" + containerId + "' stopped, mount was deleted");
			} else if (running) {
				getLog().debug("Container with id '" + containerId + "' found running");
			} else {
				getLog().debug("Container with id '" + containerId + "' found stopped");
			}
		} else {
			containerId = createContainer();
		}

		if (!running) {
			startContainer(containerId);
		}

		copyKubeConfigToMountedWorkingDirectory(containerId);
		awaitK3sNodesAndPodsReady();
	}

	private String createContainer() throws MojoExecutionException {

		// host config

		var ports = new ArrayList<PortBinding>();
		ports.add(new PortBinding(Binding.bindPort(portKubeApi), ExposedPort.tcp(portKubeApi)));
		portBindings.stream().map(PortBinding::parse).forEach(ports::add);
		var hostConfig = new HostConfig()
				.withPrivileged(true)
				.withBinds(new Bind(getWorkingDir().toString(), new Volume("/k3s")))
				.withPortBindings(ports);

		// container

		var command = List.of("server",
				"--disable-cloud-controller",
				"--disable-network-policy",
				"--disable-helm-controller",
				"--disable=metrics-server",
				"--disable=local-storage",
				"--disable=servicelb",
				"--disable=traefik",
				"--https-listen-port=" + portKubeApi);

		var containerId = dockerClient()
				.createContainerCmd(dockerImage())
				.withName(DockerUtil.CONTAINER_NAME)
				.withCmd(command)
				.withExposedPorts(ports.stream().map(PortBinding::getExposedPort).collect(Collectors.toList()))
				.withLabels(Map.of(DockerUtil.CONTAINER_LABEL, Boolean.TRUE.toString()))
				.withHostConfig(hostConfig)
				.exec().getId();
		getLog().info("Container  with id '" + containerId + "' created, image: " + dockerImage());
		getLog().info("k3s " + command.stream().collect(Collectors.joining(" ")));

		return containerId;
	}

	private void startContainer(String containerId) throws MojoExecutionException {

		// check mount path for manifests and kubectl file

		try {
			Files.createDirectories(getWorkingDir());
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to create workdir " + getWorkingDir(), e);
		}

		// start k3s
		var started = Instant.now();
		dockerClient().startContainerCmd(containerId).exec();
		getLog().info("Container with id '" + containerId + "' starting");

		// logs to console and wait for startup

		var k3sStarted = new AtomicBoolean();
		var callback = new DockerLogCallback(getLog(), streamLogs, "[k3s] ") {
			@Override
			public void onNext(Frame frame) {
				super.onNext(frame);
				if (new String(frame.getPayload()).contains("k3s is up and running")) {
					k3sStarted.set(true);
				}
			}
		};
		dockerClient().logContainerCmd(containerId)
				.withStdOut(true)
				.withStdErr(true)
				.withFollowStream(true)
				.withSince((int) started.getEpochSecond())
				.exec(callback);
		Await.await("k3s is up and running")
				.timeout(Duration.ofSeconds(nodeTimeout))
				.onTimeout(callback::replayOnWarn)
				.until(k3sStarted::get);
		getLog().info("k3s is up and running");
	}

	private void awaitK3sNodesAndPodsReady() throws MojoExecutionException {

		// wait for nodes and pods to get ready

		Await.await("k3s master node ready").until(kubernetes()::isNodeReady);
		Await.await("k3s pods ready").timeout(Duration.ofSeconds(podTimeout)).until(kubernetes()::isPodsReady);

		// wait for service account, see https://github.com/kubernetes/kubernetes/issues/66689

		Await.await("k3s service account ready").until(kubernetes()::isServiceAccountReady);

		getLog().info("k3s node ready");
	}

	private void copyKubeConfigToMountedWorkingDirectory(String containerId) throws MojoExecutionException {

		var command = "install -m 666 /etc/rancher/k3s/k3s.yaml /k3s/kubeconfig.yaml";
		var execId = dockerClient().execCreateCmd(containerId)
				.withCmd("/bin/sh", "-c", command)
				.withAttachStdout(true)
				.withAttachStderr(true)
				.exec().getId();

		var callback = new DockerLogCallback(getLog(), true, "[install] ");
		dockerClient().execStartCmd(execId).exec(callback);
		Await.await(command).onTimeout(callback::replayOnWarn).until(callback::isCompleted);

		var response = dockerClient().inspectExecCmd(execId).exec();
		if (response.getExitCodeLong() != 0) {
			callback.replayOnWarn();
			throw new MojoExecutionException("install returned exit code " + response.getExitCodeLong());
		}
	}
}
