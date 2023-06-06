package io.kokuwa.maven.k3s.mojo;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
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
 * @author stephan.schnabel@posteo.de
 * @since 1.0.0
 */
@Mojo(name = "run", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class RunMojo extends K3sMojo {

	/**
	 * k3s image registry.
	 *
	 * @since 1.0.0
	 */
	@Setter @Parameter(property = "k3s.imageRegistry")
	private String imageRegistry;

	/**
	 * k3s image repository.
	 *
	 * @since 1.0.0
	 */
	@Setter @Parameter(property = "k3s.imageRepository", defaultValue = "docker.io/rancher/k3s")
	private String imageRepository;

	/**
	 * k3s image tag.
	 *
	 * @since 1.0.0
	 */
	@Setter @Parameter(property = "k3s.imageTag", defaultValue = "latest")
	private String imageTag;

	/**
	 * Disable servicelb.
	 *
	 * @since 1.0.0
	 */
	@Setter @Parameter(property = "k3s.disableServicelb", defaultValue = "false")
	private boolean disableServicelb;

	/**
	 * Disable helm controller.
	 *
	 * @since 1.0.0
	 */
	@Setter @Parameter(property = "k3s.disableHelmController", defaultValue = "true")
	private boolean disableHelmController;

	/**
	 * Disable local storage.
	 *
	 * @since 1.0.0
	 */
	@Setter @Parameter(property = "k3s.disableLocalStorage", defaultValue = "true")
	private boolean disableLocalStorage;

	/**
	 * Disable metrics-server.
	 *
	 * @since 1.0.0
	 */
	@Setter @Parameter(property = "k3s.disableMetricsServer", defaultValue = "true")
	private boolean disableMetricsServer;

	/**
	 * Disable traefik.
	 *
	 * @since 1.0.0
	 */
	@Setter @Parameter(property = "k3s.disableTraefik", defaultValue = "true")
	private boolean disableTraefik;

	/**
	 * Disable cloud-controller.
	 *
	 * @since 1.0.0
	 */
	@Setter @Parameter(property = "k3s.disableCloudController", defaultValue = "true")
	private boolean disableCloudController;

	/**
	 * Disable network-policy.
	 *
	 * @since 1.0.0
	 */
	@Setter @Parameter(property = "k3s.disableNetworkPolicy", defaultValue = "true")
	private boolean disableNetworkPolicy;

	/**
	 * Additional port bindings e.g. 8080:8080.
	 *
	 * @since 1.0.0
	 */
	@Setter @Parameter(property = "k3s.portBindings")
	private List<String> portBindings = new ArrayList<>();

	/**
	 * KubeApi port to expose to host.
	 *
	 * @since 1.0.0
	 */
	@Setter @Parameter(property = "k3s.portKubeApi", defaultValue = "6443")
	private int portKubeApi;

	/**
	 * Fail if docker container from previous run exists.
	 *
	 * @since 1.0.0
	 */
	@Setter @Parameter(property = "k3s.failIfExists", defaultValue = "true")
	private boolean failIfExists;

	/**
	 * Replace existing docker container from previous run.
	 *
	 * @since 1.0.0
	 */
	@Setter @Parameter(property = "k3s.replaceIfExists", defaultValue = "false")
	private boolean replaceIfExists;

	/**
	 * Timeout in seconds to wait for nodes getting ready.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.nodeTimeout", defaultValue = "30")
	private Duration nodeTimeout;

	/**
	 * Skip running of k3s.
	 *
	 * @since 1.0.0
	 */
	@Setter @Parameter(property = "k3s.skipRun", defaultValue = "false")
	private boolean skipRun;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipRun)) {
			return;
		}

		var container = createK3sContainer();

		// start k3s

		if (!getDocker().isRunning(container)) {
			getDocker().startContainer(container);
		}

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
		getDocker().logContainer(container, callback);
		Await.await(getLog(), "k3s is up and running")
				.timeout(nodeTimeout)
				.onTimeout(callback::replayOnWarn)
				.until(k3sStarted::get);
		getLog().info("k3s api available: KUBECONFIG=" + getKubeConfig() + " kubectl get all --all-namespaces");

		copyKubeConfigToMountedWorkingDirectory(container);
	}

	private Container createK3sContainer() throws MojoExecutionException {

		var container = getDocker().getContainer().orElse(null);
		if (container != null) {
			var containerId = container.getId();
			if (failIfExists) {
				throw new MojoExecutionException("Container with id '" + containerId
						+ "' found. Please remove that container or set 'k3s.failIfExists' to false.");
			} else if (replaceIfExists) {
				getLog().info("Container with id '" + containerId + "' found, replacing");
				getDocker().removeContainer(container);
			} else {
				getLog().warn("Container with id '" + containerId + "' found, skip creating");
				return container;
			}
		}

		// get image name

		if (imageTag.equals("latest")) {
			getLog().warn("Using image tag 'latest' is unstable.");
		}
		var image = (imageRegistry == null ? "" : imageRegistry + "/") + imageRepository + ":" + imageTag;

		// pull image if not present

		if (getDocker().findImage(image).isPresent()) {
			getLog().debug("Docker image " + image + "found.");
		} else {
			var callback = getDocker().pullImage(image);
			Await.await(getLog(), "pull images").timeout(Duration.ofSeconds(300)).until(callback::isCompleted);
			if (!callback.isSuccess()) {
				throw new MojoExecutionException("Failed to pull image " + image);
			}
			getLog().info("Docker image " + image + " pulled");
		}

		// check mount path for manifests and kubectl file, deletes leftover files from previous run

		try {
			try {
				if (Files.exists(getMountDir())) {
					FileUtils.forceDelete(getMountDir().toFile());
				}
			} catch (IOException e) {
				throw new MojoExecutionException("Failed to delete directory at " + getMountDir(), e);
			}
			Files.createDirectories(getManifestsDir());
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to create directories", e);
		}

		// create command

		var command = new ArrayList<>(List.of("server", "--https-listen-port=" + portKubeApi));
		if (disableCloudController) {
			command.add("--disable-cloud-controller");
		}
		if (disableNetworkPolicy) {
			command.add("--disable-network-policy");
		}
		if (disableMetricsServer) {
			command.add("--disable=metrics-server");
		}
		if (disableServicelb) {
			command.add("--disable=servicelb");
		}
		if (disableHelmController) {
			command.add("--disable-helm-controller");
		}
		if (disableLocalStorage) {
			command.add("--disable=local-storage");
		}
		if (disableTraefik) {
			command.add("--disable=traefik");
		}
		getLog().info("k3s " + command.stream().collect(Collectors.joining(" ")));

		var ports = new ArrayList<>(portBindings);
		ports.add(portKubeApi + ":" + portKubeApi);
		getDocker().createVolume();
		return getDocker().createContainer(image, getMountDir(), ports, command);
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

	// setter

	public void setNodeTimeout(int nodeTimeout) {
		this.nodeTimeout = Duration.ofSeconds(nodeTimeout);
	}
}
