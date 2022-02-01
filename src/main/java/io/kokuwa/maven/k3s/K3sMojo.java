package io.kokuwa.maven.k3s;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

import io.kokuwa.maven.k3s.util.DockerUtil;
import io.kokuwa.maven.k3s.util.Kubernetes;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import lombok.Setter;

/**
 * Base class for all mojos of this plugin.
 */
public abstract class K3sMojo extends AbstractMojo {

	/** k3s image registry. */
	@Setter @Parameter(property = "k3s.imageRegistry")
	private String imageRegistry;

	/** k3s image repository. */
	@Setter @Parameter(property = "k3s.imageRepository", defaultValue = "rancher/k3s")
	private String imageRepository = "rancher/k3s";

	/** k3s image tag. */
	@Setter @Parameter(property = "k3s.imageTag", defaultValue = "latest")
	private String imageTag = "latest";

	/** k3s working directory. This directory is mounted into docker container. */
	@Setter @Parameter(property = "k3s.workdir", defaultValue = "target/k3s")
	private File workingDir = new File("target/k3s");

	/** Skip plugin. */
	@Setter @Parameter(property = "k3s.skip", defaultValue = "false")
	private boolean skip = false;

	protected Path getWorkingDir() {
		return workingDir.toPath().toAbsolutePath();
	}

	protected Path getKubeConfig() {
		return getWorkingDir().resolve("kubeconfig.yaml");
	}

	protected boolean isSkip(boolean skipMojo) {
		return skip || skipMojo;
	}

	// docker

	private static String dockerImage;
	private static DockerClient dockerClient;

	protected String dockerImage() {
		if (dockerImage == null) {
			dockerImage = (imageRegistry == null ? "" : imageRegistry + "/") + imageRepository + ":" + imageTag;
			if ("latest".equals(imageTag)) {
				getLog().warn("Using image tag 'latest' is unstable.");
			}
		}
		return dockerImage;
	}

	protected DockerClient dockerClient() throws MojoExecutionException {
		if (dockerClient == null) {

			// create client

			var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
			var httpClient = new ZerodepDockerHttpClient.Builder().dockerHost(config.getDockerHost()).build();
			dockerClient = DockerClientImpl.getInstance(config, httpClient);

			// check if docker is available

			try {
				dockerClient.pingCmd().exec();
			} catch (RuntimeException e) {
				throw new MojoExecutionException("Unable to communicate with docker", e.getCause());
			}
		}
		return dockerClient;
	}

	protected DockerUtil dockerUtil() throws MojoExecutionException {
		return new DockerUtil(dockerClient());
	}

	// kubernetes

	private static Kubernetes kubernetes;

	protected Kubernetes kubernetes() throws MojoExecutionException {
		if (kubernetes == null) {
			var kubeconfig = getKubeConfig();
			if (!Files.exists(kubeconfig)) {
				throw new MojoExecutionException("Kube config not found at " + kubeconfig);
			}
			try {
				kubernetes = new Kubernetes(new CoreV1Api(Config.fromConfig(kubeconfig.toString())));
			} catch (IOException e) {
				throw new MojoExecutionException("Failed to read kube config", e);
			}
		}
		return kubernetes;
	}

	protected void reset() {
		kubernetes = null;
	}
}
