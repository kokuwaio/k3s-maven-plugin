package io.kokuwa.maven.k3s.mojo;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;

import io.kokuwa.maven.k3s.K3sMojo;
import io.kokuwa.maven.k3s.util.Await;
import io.kokuwa.maven.k3s.util.DockerLogCallback;
import lombok.Setter;

/**
 * Mojo for create and start k3s.
 */
@Mojo(name = "start", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class StartMojo extends K3sMojo {

	/** Stream logs of `k3s` to maven logger. */
	@Setter @Parameter(property = "k3s.streamLogs", defaultValue = "false")
	private boolean streamLogs;

	/** Disable helm controller. */
	@Setter @Parameter(property = "k3s.disable.helmController", defaultValue = "true")
	private boolean disableHelmController;

	/** Disable local storage. */
	@Setter @Parameter(property = "k3s.disable.localStorage", defaultValue = "true")
	private boolean disableLocalStorage;

	/** Disable traefik. */
	@Setter @Parameter(property = "k3s.disable.traefik", defaultValue = "true")
	private boolean disableTraefik;

	/** KubeApi port to expose to host. */
	@Setter @Parameter(property = "k3s.portKubeApi", defaultValue = "6443")
	private int portKubeApi;

	/** Timeout in seconds to wait for nodes getting ready. */
	@Setter @Parameter(property = "k3s.nodeTimeout", defaultValue = "120")
	private int nodeTimeout;

	/** Timeout in seconds to wait for pods getting ready. */
	@Setter @Parameter(property = "k3s.podTimeout", defaultValue = "300")
	private int podTimeout;

	/** Wait for pods getting ready. */
	@Setter @Parameter(property = "k3s.podWait", defaultValue = "false")
	private boolean podWait;

	/** Skip starting of k3s container. */
	@Setter @Parameter(property = "k3s.skipStart", defaultValue = "false")
	private boolean skipStart;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipStart)) {
			return;
		}

		var container = docker.getK3sContainer().orElse(null);
		var running = container != null && docker.isRunning(container);
		if (container != null) {
			if (!Files.exists(getKubeConfig()) && running) {
				running = false;
				docker.stopContainer(container);
				log.info("Container with id '{}' stopped, mount was deleted", container.getId());
			} else if (running) {
				log.debug("Container with id '{}' found running", container.getId());
			} else {
				log.debug("Container with id '{}' found stopped", container.getId());
			}
		} else {
			container = createContainer(getDockerImage());
		}

		if (!running) {
			startK3sContainer(container);
		}

		copyKubeConfigToMountedWorkingDirectory(container);
		awaitK3sNodesAndPodsReady();
	}

	private Container createContainer(String dockerImage) {

		var command = new ArrayList<>(List.of("server",
				"--disable-cloud-controller",
				"--disable-network-policy",
				"--disable=metrics-server",
				"--disable=servicelb",
				"--docker",
				"--https-listen-port=" + portKubeApi));
		if (disableHelmController) {
			command.add("--disable-helm-controller");
		}
		if (disableLocalStorage) {
			command.add("--disable=local-storage");
		}
		if (disableTraefik) {
			command.add("--disable=traefik");
		}
		log.info("k3s " + command.stream().collect(Collectors.joining(" ")));

		return docker.createK3sContainer(dockerImage, getWorkDir(), command);
	}

	private void startK3sContainer(Container container) throws MojoExecutionException {

		// check mount path for manifests and kubectl file

		try {
			Files.createDirectories(getWorkDir());
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to create workdir " + getWorkDir(), e);
		}

		// start k3s

		var started = Instant.now();
		docker.startContainer(container);

		// logs to console and wait for startup

		var k3sStarted = new AtomicBoolean();
		var callback = new DockerLogCallback(LoggerFactory.getLogger("io.kokuwa.maven.k3s.docker.k3s"), streamLogs) {
			@Override
			public void onNext(Frame frame) {
				super.onNext(frame);
				if (new String(frame.getPayload()).contains("k3s is up and running")) {
					k3sStarted.set(true);
				}
			}
		};
		docker.logContainer(container, started, callback);
		Await.await("k3s is up and running")
				.timeout(Duration.ofSeconds(nodeTimeout))
				.onTimeout(callback::replayOnWarn)
				.until(k3sStarted::get);
		log.info("k3s is up and running");
	}

	private void awaitK3sNodesAndPodsReady() throws MojoExecutionException {
		var kubernetes = getKubernetesClient();

		// wait for nodes get ready

		Await.await("k3s master node ready").until(kubernetes::isNodeReady);

		// wait for service account, see https://github.com/kubernetes/kubernetes/issues/66689

		Await.await("k3s service account ready").until(kubernetes::isServiceAccountReady);

		// wait for pods get ready

		if (podWait) {
			Await.await("k3s pods ready").timeout(Duration.ofSeconds(podTimeout)).until(kubernetes::isPodsReady);
		}

		log.info("k3s node ready");
	}

	private void copyKubeConfigToMountedWorkingDirectory(Container container) throws MojoExecutionException {
		var command = "install -m 666 /etc/rancher/k3s/k3s.yaml /k3s/kubeconfig.yaml";
		var callback = new DockerLogCallback(LoggerFactory.getLogger("io.kokuwa.maven.k3s.docker.install"), true);
		docker.exec("install", container, cmd -> cmd.withCmd("/bin/sh", "-c", command), callback);
	}
}
