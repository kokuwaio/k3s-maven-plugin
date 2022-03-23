package io.kokuwa.maven.k3s;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kokuwa.maven.k3s.util.Docker;
import io.kokuwa.maven.k3s.util.Kubernetes;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import lombok.Setter;

/**
 * Base class for all mojos of this plugin.
 */
public abstract class K3sMojo extends AbstractMojo {

	protected final Logger log = LoggerFactory.getLogger(getClass());
	protected final Docker docker = new Docker();

	/** k3s image registry. */
	@Setter @Parameter(property = "k3s.imageRegistry")
	private String imageRegistry;

	/** k3s image repository. */
	@Setter @Parameter(property = "k3s.imageRepository", defaultValue = "rancher/k3s")
	private String imageRepository = "rancher/k3s";

	/** k3s image tag. */
	@Setter @Parameter(property = "k3s.imageTag")
	private String imageTag;

	/** k3s working directory. This directory is mounted into docker container. */
	@Setter @Parameter(property = "k3s.workdir", defaultValue = "target/k3s")
	private File workDir;

	/** Skip plugin. */
	@Setter @Parameter(property = "k3s.skip", defaultValue = "false")
	private boolean skip = false;

	protected Path getWorkDir() {
		return workDir.toPath().toAbsolutePath();
	}

	protected Path getKubeConfig() {
		return getWorkDir().resolve("kubeconfig.yaml");
	}

	protected boolean isSkip(boolean skipMojo) {
		return skip || skipMojo;
	}

	protected String getDockerImage() {
		if (imageTag == null) {
			imageTag = "v1.23.4-k3s1";
			log.warn("No image tag provided, '{}' will be used. This will change in newer versions.", imageTag);
		} else if (imageTag.equals("latest")) {
			log.warn("Using image tag 'latest' is unstable.");
		}
		return (imageRegistry == null ? "" : imageRegistry + "/") + imageRepository + ":" + imageTag;
	}

	protected Kubernetes getKubernetesClient() throws MojoExecutionException {
		var kubeconfig = getKubeConfig();
		if (!Files.exists(kubeconfig)) {
			throw new MojoExecutionException("Kube config not found at " + kubeconfig);
		}
		try {
			return new Kubernetes(new CoreV1Api(Config.fromConfig(kubeconfig.toString())));
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to read kube config", e);
		}
	}
}
