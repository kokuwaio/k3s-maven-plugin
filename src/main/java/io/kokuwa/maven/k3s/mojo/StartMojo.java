package io.kokuwa.maven.k3s.mojo;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;

import io.kokuwa.maven.k3s.util.Await;
import io.kokuwa.maven.k3s.util.DockerLogCallback;
import lombok.Setter;

/**
 * Mojo for start k3s container.
 *
 * @since 0.1.0
 */
@Mojo(name = "start", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class StartMojo extends K3sMojo {

	/**
	 * Timeout in seconds to wait for nodes getting ready.
	 *
	 * @since 0.1.0
	 */
	@Setter @Parameter(property = "k3s.nodeTimeout", defaultValue = "120")
	private int nodeTimeout;

	/**
	 * Timeout in seconds to wait for pods getting ready.
	 *
	 * @since 0.1.0
	 */
	@Setter @Parameter(property = "k3s.podTimeout", defaultValue = "300")
	private int podTimeout;

	/**
	 * Wait for pods getting ready.
	 *
	 * @since 0.2.0
	 */
	@Setter @Parameter(property = "k3s.podWait", defaultValue = "false")
	private boolean podWait;

	/**
	 * Skip starting of k3s container.
	 *
	 * @since 0.1.0
	 */
	@Setter @Parameter(property = "k3s.skipStart", defaultValue = "false")
	private boolean skipStart;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipStart)) {
			return;
		}

		var containerOptional = getDocker().getContainer();
		if (containerOptional.isEmpty()) {
			throw new MojoExecutionException("No k3s container found");
		}
		var container = containerOptional.get();

		var running = getDocker().isRunning(container);
		if (running) {
			getLog().info("Container with id '" + container.getId() + "' found running, skip start");
		} else {
			getLog().debug("Container with id '" + container.getId() + "' found stopped");
			startK3sContainer(container);
		}

		copyKubeConfigToMountedWorkingDirectory(container);
		awaitK3sNodesAndPodsReady();
	}

	private void startK3sContainer(Container container) throws MojoExecutionException {

		// start k3s

		var started = Instant.now();
		getDocker().startContainer(container);

		// logs to console and wait for startup

		var k3sStarted = new AtomicBoolean();
		var callback = new DockerLogCallback(getLog()) {
			@Override
			public void onNext(Frame frame) {
				super.onNext(frame);
				if (new String(frame.getPayload()).contains("k3s is up and running")) {
					k3sStarted.set(true);
				}
			}
		};
		getDocker().logContainer(container, started, callback);
		Await.await(getLog(), "k3s is up and running")
				.timeout(Duration.ofSeconds(nodeTimeout))
				.onTimeout(callback::replayOnWarn)
				.until(k3sStarted::get);
		getLog().info("k3s ready, connect via: KUBECONFIG=" + getKubeConfig() + " kubectl get all --all-namespaces");
	}

	private void awaitK3sNodesAndPodsReady() throws MojoExecutionException {
		var kubernetes = getKubernetesClient();

		// wait for nodes get ready

		Await.await(getLog(), "k3s master node ready").until(kubernetes::isNodeReady);

		// wait for pods get ready

		if (podWait) {
			Await.await(getLog(), "k3s pods ready")
					.timeout(Duration.ofSeconds(podTimeout))
					.until(kubernetes::isPodsReady);
		}

		getLog().info("k3s node ready");
	}

	private void copyKubeConfigToMountedWorkingDirectory(Container container) throws MojoExecutionException {
		var command = "install -m 666 /etc/rancher/k3s/k3s.yaml /k3s/kubeconfig.yaml";
		var callback = new DockerLogCallback(getLog());
		var timeout = Duration.ofSeconds(30);
		var result = getDocker().exec("install", container, cmd -> cmd.withCmd("/bin/sh", "-c", command), callback,
				timeout);
		if (result.getExitCode() != 0) {
			callback.replayOnWarn();
			throw new MojoExecutionException("install returned exit code " + result.getExitCode());
		}
	}
}
