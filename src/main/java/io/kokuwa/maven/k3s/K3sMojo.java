package io.kokuwa.maven.k3s;

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
import lombok.Getter;
import lombok.Setter;

/**
 * Base class for all mojos of this plugin.
 */
public abstract class K3sMojo extends AbstractMojo {

	private static final String IMAGE_REPOSITORY = "rancher/k3s";
	private static final String IMAGE_TAG = "latest";

	@Getter
	private final Path workdir = Path.of("target/k3s").toAbsolutePath();
	@Getter
	private final Path kubeconfig = workdir.resolve("kubeconfig.yaml");

	// parameter

	@Getter	@Setter	@Parameter(property = "k3s.image.registry")
	private String imageRegistry;
	@Getter	@Setter	@Parameter(property = "k3s.image.repository", defaultValue = IMAGE_REPOSITORY)
	private String imageRepository = IMAGE_REPOSITORY;
	@Getter	@Setter	@Parameter(property = "k3s.image.tag", defaultValue = IMAGE_TAG)
	private String imageTag = IMAGE_TAG;
	@Getter	@Setter	@Parameter(property = "k3s.image.pullAlways", defaultValue = "false")
	private boolean pullAlways = false;
	@Getter	@Setter	@Parameter(property = "k3s.skip", defaultValue = "false")
	private boolean skip = false;

	// docker

	static DockerUtil dockerUtil;
	static String dockerImage;
	static DockerClient dockerClient;

	public String dockerImage() {
		if (dockerImage == null) {
			dockerImage = (imageRegistry == null ? "" : imageRegistry + "/") + imageRepository + ":" + imageTag;
			if ("latest".equals(getImageTag())) {
				getLog().warn("Using image tag 'latest' is unstable.");
			}
		}
		return dockerImage;
	}

	public DockerClient dockerClient() throws MojoExecutionException {
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

	public DockerUtil dockerUtil() throws MojoExecutionException {
		return new DockerUtil(dockerClient());
	}

	// kubernetes

	static Kubernetes kubernetes;

	public Kubernetes kubernetes() throws MojoExecutionException {
		if (kubernetes == null) {
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

	public void reset() {
		kubernetes = null;
	}
}
