package io.kokuwa.maven.k3s.mojo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kokuwa.maven.k3s.util.Docker;
import io.kokuwa.maven.k3s.util.Kubernetes;
import io.kubernetes.client.util.Config;
import lombok.Setter;

/**
 * Base class for all mojos of this plugin.
 */
public abstract class K3sMojo extends AbstractMojo {

	final Logger log = LoggerFactory.getLogger(getClass());
	final Docker docker = new Docker();

	/** Cachedir mounted to `/var/lib/rancher/k3s/agent`. */
	@Setter @Parameter(property = "k3s.cacheDir", defaultValue = "${user.home}/.kube/k3s-maven-plugin")
	private String cacheDir;

	/** Skip plugin. */
	@Setter @Parameter(property = "k3s.skip", defaultValue = "false")
	private boolean skip = false;

	boolean isSkip(boolean skipMojo) {
		return skip || skipMojo;
	}

	Kubernetes getKubernetesClient() throws MojoExecutionException {
		var kubeconfig = getKubeConfig();
		if (!Files.exists(kubeconfig)) {
			throw new MojoExecutionException("Kube config not found at " + kubeconfig);
		}
		try {
			return new Kubernetes(Config.fromConfig(kubeconfig.toString()));
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to read kube config", e);
		}
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
