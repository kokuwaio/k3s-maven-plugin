package io.kokuwa.maven.k3s.mojo;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.kokuwa.maven.k3s.util.Await;
import lombok.Setter;

/**
 * Mojo for create and start k3s.
 */
@Mojo(name = "create", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class CreateMojo extends K3sMojo {

	/** k3s image registry. */
	@Setter @Parameter(property = "k3s.imageRegistry")
	private String imageRegistry;

	/** k3s image repository. */
	@Setter @Parameter(property = "k3s.imageRepository", defaultValue = "rancher/k3s")
	private String imageRepository = "rancher/k3s";

	/** k3s image tag. */
	@Setter @Parameter(property = "k3s.imageTag")
	private String imageTag;

	/** Disable helm controller. */
	@Setter @Parameter(property = "k3s.disableHelmController", defaultValue = "true")
	private boolean disableHelmController;

	/** Disable local storage. */
	@Setter @Parameter(property = "k3s.disableLocalStorage", defaultValue = "true")
	private boolean disableLocalStorage;

	/** Disable traefik. */
	@Setter @Parameter(property = "k3s.disableTraefik", defaultValue = "true")
	private boolean disableTraefik;

	/** Additional port bindings e.g. `8080:8080`. */
	@Setter @Parameter(property = "k3s.portBindings")
	private List<String> portBindings = new ArrayList<>();

	/** KubeApi port to expose to host. */
	@Setter @Parameter(property = "k3s.portKubeApi", defaultValue = "6443")
	private int portKubeApi;

	/** Skip creation of k3s container. */
	@Setter @Parameter(property = "k3s.skipCreate", defaultValue = "false")
	private boolean skipCreate;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipCreate)) {
			return;
		}

		var container = docker.getContainer();
		if (container.isPresent()) {
			log.debug("Container with id '{}' found", container.get().getId());
			return;
		}

		// get image name

		if (imageTag == null) {
			imageTag = "v1.23.5-k3s1";
			log.warn("No image tag provided, '{}' will be used. This will change in newer versions.", imageTag);
		} else if (imageTag.equals("latest")) {
			log.warn("Using image tag 'latest' is unstable.");
		}
		var image = (imageRegistry == null ? "" : imageRegistry + "/") + imageRepository + ":" + imageTag;

		// pull image if not present

		if (docker.findImage(image).isPresent()) {
			log.debug("Docker image {} found.");
		} else {
			var callback = docker.pullImage(image);
			Await.await("pull images").timeout(Duration.ofSeconds(300)).until(callback::isCompleted);
			if (!callback.isSuccess()) {
				throw new MojoExecutionException("Failed to pull image");
			}
		}

		// check mount path for manifests and kubectl file

		try {
			Files.createDirectories(getManifestsDir());
			Files.createDirectories(getRancherDir());
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to create directories", e);
		}

		// create command

		var command = new ArrayList<>(List.of("server",
				"--disable-cloud-controller",
				"--disable-network-policy",
				"--disable=metrics-server",
				"--disable=servicelb",
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

		var ports = new ArrayList(portBindings);
		ports.add(portKubeApi + ":" + portKubeApi);
		docker.createContainer(image, getMountDir(), getRancherDir(), ports, command);
	}
}
