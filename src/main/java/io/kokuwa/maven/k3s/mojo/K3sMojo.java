package io.kokuwa.maven.k3s.mojo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;

import io.kokuwa.maven.k3s.util.DebugLog;
import io.kokuwa.maven.k3s.util.Docker;
import io.kokuwa.maven.k3s.util.Kubernetes;
import io.kubernetes.client.util.Config;
import lombok.Setter;

/**
 * Base class for all mojos of this plugin.
 */
public abstract class K3sMojo extends AbstractMojo {

	/** Cachedir mounted to `/var/lib/rancher/k3s/agent`. */
	@Setter @Parameter(property = "k3s.cacheDir", defaultValue = "${user.home}/.kube/k3s-maven-plugin")
	private String cacheDir;

	/**
	 * Enable debuging of docker and k3s logs.
	 *
	 * @since 1.0.0
	 */
	@Setter @Parameter(property = "k3s.debug", defaultValue = "false")
	private boolean debug;

	/** Skip plugin. */
	@Setter @Parameter(property = "k3s.skip", defaultValue = "false")
	private boolean skip = false;

	/** Name for the create docker container. */
	@Setter @Parameter(defaultValue = "k3s-maven-plugin", readonly = true)
	private String containerName;

	/** Name for the create volume container. */
	@Setter @Parameter(defaultValue = "k3s-maven-plugin", readonly = true)
	private String volumeName;

	// generic methods

	private Log log;
	private Docker docker;

	public boolean isSkip(boolean skipMojo) {
		return skip || skipMojo;
	}

	Kubernetes getKubernetesClient() throws MojoExecutionException {
		var kubeconfig = getKubeConfig();
		if (!Files.exists(kubeconfig)) {
			throw new MojoExecutionException("Kube config not found at " + kubeconfig);
		}
		try {
			return new Kubernetes(getLog(), Config.fromConfig(kubeconfig.toString()));
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to read kube config", e);
		}
	}

	@Override
	public Log getLog() {
		return log == null ? log = new DebugLog(super.getLog(), debug) : log;
	}

	public Docker getDocker() {
		return docker == null ? docker = new Docker(containerName, volumeName, getLog()) : docker;
	}

	// directories

	Path getCacheDir() {
		return Paths.get(cacheDir);
	}

	Path getMountDir() {
		return getCacheDir().resolve("mount");
	}

	Path getManifestsDir() {
		return getMountDir().resolve("manifests");
	}

	Path getKubeConfig() {
		return getMountDir().resolve("kubeconfig.yaml");
	}
}
