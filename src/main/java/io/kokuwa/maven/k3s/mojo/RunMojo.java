package io.kokuwa.maven.k3s.mojo;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.kokuwa.maven.k3s.util.Await;

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
	@Parameter(property = "k3s.imageRegistry")
	private String imageRegistry;

	/**
	 * k3s image repository.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.imageRepository", defaultValue = "docker.io/rancher/k3s")
	private String imageRepository;

	/**
	 * k3s image tag.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.imageTag", defaultValue = "latest")
	private String imageTag;

	/**
	 * Disable servicelb.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.disableServicelb", defaultValue = "false")
	private boolean disableServicelb;

	/**
	 * Disable helm controller.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.disableHelmController", defaultValue = "true")
	private boolean disableHelmController;

	/**
	 * Disable local storage.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.disableLocalStorage", defaultValue = "true")
	private boolean disableLocalStorage;

	/**
	 * Disable metrics-server.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.disableMetricsServer", defaultValue = "true")
	private boolean disableMetricsServer;

	/**
	 * Disable traefik.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.disableTraefik", defaultValue = "true")
	private boolean disableTraefik;

	/**
	 * Disable coredns.
	 *
	 * @since 1.5.1
	 */
	@Parameter(property = "k3s.disableCoredns", defaultValue = "false")
	private boolean disableCoredns;

	/**
	 * Disable cloud-controller.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.disableCloudController", defaultValue = "true")
	private boolean disableCloudController;

	/**
	 * Disable network-policy.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.disableNetworkPolicy", defaultValue = "true")
	private boolean disableNetworkPolicy;

	/**
	 * Disables containerd's fallback default registry endpoint when a mirror is configured for that registry.
	 *
	 * @since 1.5.1
	 */
	@Parameter(property = "k3s.disableDefaultRegistryEndpoint", defaultValue = "false")
	private boolean disableDefaultRegistryEndpoint;

	/**
	 * Additional port bindings e.g. 8080:8080.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.portBindings")
	private List<String> portBindings = new ArrayList<>();

	/**
	 * KubeApi port to expose to host.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.portKubeApi", defaultValue = "6443")
	private int portKubeApi;

	/**
	 * Cluster Domain.
	 *
	 * @see "https://docs.k3s.io/cli/server#networking"
	 * @since 1.2.0
	 */
	@Parameter(property = "k3s.clusterDomain")
	private String clusterDomain;

	/**
	 * IPv4 Cluster IP for coredns service.
	 *
	 * @see "https://docs.k3s.io/cli/server#networking"
	 * @since 1.2.0
	 */
	@Parameter(property = "k3s.clusterDns")
	private String clusterDns;

	/**
	 * IPv4/IPv6 network CIDRs to use for pod IPs.
	 *
	 * @see "https://docs.k3s.io/cli/server#networking"
	 * @since 1.2.0
	 */
	@Parameter(property = "k3s.clusterCidr")
	private String clusterCidr;

	/**
	 * IPv4/IPv6 network CIDRs to use for service IPs.
	 *
	 * @see "https://docs.k3s.io/cli/server#networking"
	 * @since 1.2.0
	 */
	@Parameter(property = "k3s.serviceCidr")
	private String serviceCidr;

	/**
	 * Fail if docker container from previous run exists.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.failIfExists", defaultValue = "true")
	private boolean failIfExists;

	/**
	 * Replace existing docker container from previous run.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.replaceIfExists", defaultValue = "false")
	private boolean replaceIfExists;

	/**
	 * Path where kubeconfig.yaml should be placed on host.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "k3s.kubeconfig", defaultValue = "${project.build.directory}/k3s.yaml")
	private Path kubeconfig;

	/**
	 * Path to "registry.yaml" to mount to "/etc/rancher/k3s/registries.yaml".
	 *
	 * @since 1.1.0
	 */
	@Parameter(property = "k3s.registries")
	private Path registries;

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
	@Parameter(property = "k3s.skipRun", defaultValue = "false")
	private boolean skipRun;

	/**
	 * Check if dns resolves custom domains.
	 *
	 * @since 1.4.0
	 */
	@Parameter(property = "k3s.dnsResolverCheck", defaultValue = "true")
	private boolean dnsResolverCheck;

	/**
	 * Custom domain to resolve.
	 *
	 * @since 1.4.0
	 */
	@Parameter(property = "k3s.dnsResolverDomain", defaultValue = "k3s-maven-plugin.127.0.0.1.nip.io")
	private String dnsResolverDomain;

	@Override
	public void execute() throws MojoExecutionException {

		// check dns

		if (dnsResolverCheck) {
			try {
				var address = InetAddress.getByName(dnsResolverDomain).getHostAddress();
				log.debug("DNS resolved {} to {}.", dnsResolverDomain, address);
			} catch (UnknownHostException e) {
				log.warn("DNS was unable to resolve {}. Custom domains may not work!", dnsResolverDomain);
			}
		}

		if (isSkip(skipRun)) {
			return;
		}

		// check container

		var create = true;
		var restart = false;
		var container = getDocker().getContainer().orElse(null);
		if (container != null) {
			if (failIfExists) {
				throw new MojoExecutionException("Container with id '" + container.id
						+ "' found. Please remove that container or set 'k3s.failIfExists' to false.");
			} else if (replaceIfExists) {
				log.info("Container with id '{}' found, replacing", container.id);
				getDocker().removeContainer();
			} else if (!container.isRunning()) {
				log.warn("Container with id '{}' found in stopped state, restart container", container.id);
				create = false;
				restart = true;
			} else {
				log.warn("Container with id '{}' found, skip creating", container.id);
				create = false;
			}
		}

		// create container

		if (create || restart) {

			if (create) {
				createAndStartK3sContainer();
			} else if (restart) {
				getDocker().startContainer();
			}

			// wait for k3s api to be ready

			var await = Await.await(log, "k3s api available").timeout(nodeTimeout);
			getDocker().waitForLog(await, output -> output.stream().anyMatch(l -> l.contains("k3s is up and running")));

			// write file that k3s started

			getMarker().writeStarted();
		}

		getDocker().copyFromContainer("/etc/rancher/k3s/k3s.yaml", kubeconfig);
		log.info("k3s ready: KUBECONFIG={} kubectl get all --all-namespaces", kubeconfig);
	}

	private void createAndStartK3sContainer() throws MojoExecutionException {

		// get image name

		if ("latest".equals(imageTag)) {
			log.warn("Using image tag 'latest' is unstable.");
		}
		var image = (imageRegistry == null ? "" : imageRegistry + "/") + imageRepository + ":" + imageTag;

		// k3s command

		var command = new ArrayList<>(List.of("server", "--node-name=k3s", "--https-listen-port=" + portKubeApi));
		if (clusterDomain != null) {
			command.add("--cluster-domain=" + clusterDomain);
		}
		if (clusterDns != null) {
			command.add("--cluster-dns=" + clusterDns);
		}
		if (clusterCidr != null) {
			command.add("--cluster-cidr=" + clusterCidr);
		}
		if (serviceCidr != null) {
			command.add("--service-cidr=" + serviceCidr);
		}
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
		if (disableCoredns) {
			command.add("--disable=coredns");
		}
		if (registries != null) {
			if (!Files.isRegularFile(registries)) {
				throw new MojoExecutionException("Registries file '" + registries + "' not found.");
			}
			command.add("--private-registry=/etc/rancher/k3s/registries.yaml");
			if (disableDefaultRegistryEndpoint) {
				command.add("--disable-default-registry-endpoint");
			}
		} else if (disableDefaultRegistryEndpoint) {
			log.warn("Skip setting --disable-default-registry-endpoint because no registries were configured.");
		}
		log.info("k3s {}", command.stream().collect(Collectors.joining(" ")));

		// create container

		var ports = new ArrayList<>(portBindings);
		ports.add(portKubeApi + ":" + portKubeApi);
		getDocker().createContainer(image, ports, command, registries);
		getDocker().createVolume();
	}

	// setter

	public void setKubeconfig(File kubeconfig) {
		this.kubeconfig = kubeconfig.toPath().toAbsolutePath();
	}

	public void setNodeTimeout(int nodeTimeout) {
		this.nodeTimeout = Duration.ofSeconds(nodeTimeout);
	}

	public void setImageRegistry(String imageRegistry) {
		this.imageRegistry = imageRegistry;
	}

	public void setImageRepository(String imageRepository) {
		this.imageRepository = imageRepository;
	}

	public void setImageTag(String imageTag) {
		this.imageTag = imageTag;
	}

	public void setDisableServicelb(boolean disableServicelb) {
		this.disableServicelb = disableServicelb;
	}

	public void setDisableHelmController(boolean disableHelmController) {
		this.disableHelmController = disableHelmController;
	}

	public void setDisableLocalStorage(boolean disableLocalStorage) {
		this.disableLocalStorage = disableLocalStorage;
	}

	public void setDisableMetricsServer(boolean disableMetricsServer) {
		this.disableMetricsServer = disableMetricsServer;
	}

	public void setDisableTraefik(boolean disableTraefik) {
		this.disableTraefik = disableTraefik;
	}

	public void setDisableCoredns(boolean disableCoredns) {
		this.disableCoredns = disableCoredns;
	}

	public void setDisableCloudController(boolean disableCloudController) {
		this.disableCloudController = disableCloudController;
	}

	public void setDisableNetworkPolicy(boolean disableNetworkPolicy) {
		this.disableNetworkPolicy = disableNetworkPolicy;
	}

	public void setDisableDefaultRegistryEndpoint(boolean disableDefaultRegistryEndpoint) {
		this.disableDefaultRegistryEndpoint = disableDefaultRegistryEndpoint;
	}

	public void setPortBindings(List<String> portBindings) {
		this.portBindings = portBindings;
	}

	public void setPortKubeApi(int portKubeApi) {
		this.portKubeApi = portKubeApi;
	}

	public void setFailIfExists(boolean failIfExists) {
		this.failIfExists = failIfExists;
	}

	public void setReplaceIfExists(boolean replaceIfExists) {
		this.replaceIfExists = replaceIfExists;
	}

	public void setSkipRun(boolean skipRun) {
		this.skipRun = skipRun;
	}

	public void setRegistries(File registries) {
		this.registries = registries == null ? null : registries.toPath().toAbsolutePath();
	}

	public void setClusterDns(String clusterDns) {
		this.clusterDns = clusterDns;
	}

	public void setClusterDomain(String clusterDomain) {
		this.clusterDomain = clusterDomain;
	}

	public void setClusterCidr(String clusterCidr) {
		this.clusterCidr = clusterCidr;
	}

	public void setServiceCidr(String serviceCidr) {
		this.serviceCidr = serviceCidr;
	}

	public void setDnsResolverCheck(boolean dnsResolverCheck) {
		this.dnsResolverCheck = dnsResolverCheck;
	}

	public void setDnsResolverDomain(String dnsResolverDomain) {
		this.dnsResolverDomain = dnsResolverDomain;
	}
}
