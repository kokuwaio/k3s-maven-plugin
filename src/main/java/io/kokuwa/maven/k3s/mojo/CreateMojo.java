package io.kokuwa.maven.k3s.mojo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.kokuwa.maven.k3s.AgentCacheMode;
import io.kokuwa.maven.k3s.util.Await;
import lombok.Setter;

/**
 * Mojo for create k3s docker container.
 *
 * @since 0.3.0
 */
@Mojo(name = "create", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class CreateMojo extends K3sMojo {

	/**
	 * k3s image registry.
	 *
	 * @since 0.3.0
	 */
	@Setter @Parameter(property = "k3s.imageRegistry")
	private String imageRegistry;

	/**
	 * k3s image repository.
	 *
	 * @since 0.3.0
	 */
	@Setter @Parameter(property = "k3s.imageRepository", defaultValue = "rancher/k3s")
	private String imageRepository = "rancher/k3s";

	/**
	 * k3s image tag.
	 *
	 * @since 0.3.0
	 */
	@Setter @Parameter(property = "k3s.imageTag")
	private String imageTag;

	/**
	 * Disable servicelb.
	 *
	 * @since 0.4.0
	 */
	@Setter @Parameter(property = "k3s.disableServicelb", defaultValue = "false")
	private boolean disableServicelb;

	/**
	 * Disable helm controller.
	 *
	 * @since 0.3.0
	 */
	@Setter @Parameter(property = "k3s.disableHelmController", defaultValue = "true")
	private boolean disableHelmController;

	/**
	 * Disable local storage.
	 *
	 * @since 0.3.0
	 */
	@Setter @Parameter(property = "k3s.disableLocalStorage", defaultValue = "true")
	private boolean disableLocalStorage;

	/**
	 * Disable metrics-server.
	 *
	 * @since 0.4.0
	 */
	@Setter @Parameter(property = "k3s.disableMetricsServer", defaultValue = "true")
	private boolean disableMetricsServer;

	/**
	 * Disable traefik.
	 *
	 * @since 0.3.0
	 */
	@Setter @Parameter(property = "k3s.disableTraefik", defaultValue = "true")
	private boolean disableTraefik;

	/**
	 * Disable cloud-controller.
	 *
	 * @since 0.4.0
	 */
	@Setter @Parameter(property = "k3s.disableCloudController", defaultValue = "true")
	private boolean disableCloudController;

	/**
	 * Disable network-policy.
	 *
	 * @since 0.4.0
	 */
	@Setter @Parameter(property = "k3s.disableNetworkPolicy", defaultValue = "true")
	private boolean disableNetworkPolicy;

	/**
	 * Additional port bindings e.g. 8080:8080.
	 *
	 * @since 0.3.0
	 */
	@Setter @Parameter(property = "k3s.portBindings")
	private List<String> portBindings = new ArrayList<>();

	/**
	 * KubeApi port to expose to host.
	 *
	 * @since 0.3.0
	 */
	@Setter @Parameter(property = "k3s.portKubeApi", defaultValue = "6443")
	private int portKubeApi;

	/**
	 * Fail if docker container from previous run exists.
	 *
	 * @since 0.8.0
	 */
	@Setter @Parameter(property = "k3s.failIfExists", defaultValue = "true")
	private boolean failIfExists;

	/**
	 * Replace existing docker container from previous run.
	 *
	 * @since 0.8.0
	 */
	@Setter @Parameter(property = "k3s.replaceIfExists", defaultValue = "false")
	private boolean replaceIfExists;

	/**
	 * Cache mode for k3s agent directory.
	 *
	 * @since 0.9.0
	 */
	@Setter @Parameter(property = "k3s.agentCache", defaultValue = "NONE")
	private AgentCacheMode agentCache;

	/**
	 * Skip creation of k3s container.
	 *
	 * @since 0.3.0
	 */
	@Setter @Parameter(property = "k3s.skipCreate", defaultValue = "false")
	private boolean skipCreate;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipCreate)) {
			return;
		}

		var container = docker.getContainer();
		if (container.isPresent()) {
			var containerId = container.get().getId();
			if (failIfExists) {
				throw new MojoExecutionException("Container with id '" + containerId
						+ "' found. Please remove that container or set 'k3s.failIfExists' to false.");
			} else if (replaceIfExists) {
				log.info("Container with id '{}' found, replacing", containerId);
				docker.removeContainer(container.get());
			} else {
				log.warn("Container with id '{}' found, skip creating", containerId);
				return;
			}
		}

		// get image name

		if (imageTag == null) {
			imageTag = "v1.27.1-k3s1";
			log.warn("No image tag provided, '{}' will be used. This will change in newer versions.", imageTag);
		} else if (imageTag.equals("latest")) {
			log.warn("Using image tag 'latest' is unstable.");
		}
		var image = (imageRegistry == null ? "" : imageRegistry + "/") + imageRepository + ":" + imageTag;

		// pull image if not present

		if (docker.findImage(image).isPresent()) {
			log.debug("Docker image {} found.", image);
		} else {
			var callback = docker.pullImage(image);
			Await.await("pull images").timeout(Duration.ofSeconds(300)).until(callback::isCompleted);
			if (!callback.isSuccess()) {
				throw new MojoExecutionException("Failed to pull image " + image);
			}
			log.info("Docker image {} pulled", image);
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
			if (agentCache == AgentCacheMode.HOST) {
				Files.createDirectories(getAgentDir());
			}
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
		log.info("k3s " + command.stream().collect(Collectors.joining(" ")));

		var ports = new ArrayList<>(portBindings);
		ports.add(portKubeApi + ":" + portKubeApi);
		docker.createContainer(image, agentCache, getMountDir(), getAgentDir(), ports, command);
	}

	private Path getAgentDir() {
		return getCacheDir().resolve("agent");
	}
}
