package io.kokuwa.maven.k3s.mojo;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.LoggerFactory;

import io.kokuwa.maven.k3s.K3sMojo;
import io.kokuwa.maven.k3s.util.Await;
import io.kokuwa.maven.k3s.util.DockerLogCallback;
import io.kokuwa.maven.k3s.util.Kubernetes;
import io.kubernetes.client.openapi.ApiException;
import lombok.Setter;

/**
 * Mojo for kubectl.
 */
@Mojo(name = "kubectl", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class KubectlMojo extends K3sMojo {

	/** Stream logs of `kubectl` to maven logger. */
	@Setter @Parameter(property = "k3s.kubectl.streamLogs", defaultValue = "false")
	private boolean streamLogs;

	/** Path where to find manifest files. */
	@Setter @Parameter(property = "k3s.kubectl.manifests", defaultValue = "src/test/k3s")
	private File manifests;

	/** Timeout in seconds to wait for pods getting ready. */
	@Setter @Parameter(property = "k3s.kubectl.podTimeout", defaultValue = "300")
	private int podTimeout;

	/** Command to use for applying kustomize files. */
	@Setter @Parameter(property = "k3s.kubectl.command", defaultValue = "kubectl apply -f .")
	private String command;

	/** Skip applying kubectl manifests. */
	@Setter @Parameter(property = "k3s.skipKubectl", defaultValue = "false")
	private boolean skipKubectl;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipKubectl)) {
			return;
		}

		// copy manifests to working directory

		var source = manifests.toPath().toAbsolutePath();
		var destination = getWorkDir().resolve("manifests").toAbsolutePath();
		try {
			if (Files.exists(source)) {
				if (Files.exists(destination)) {
					FileUtils.forceDelete(destination.toFile());
				}
				if (Files.isDirectory(source)) {
					FileUtils.copyDirectory(source.toFile(), destination.toFile());
				} else {
					Files.createDirectories(destination);
					Files.copy(source, destination.resolve(source.getFileName()));
				}
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to copy manifests", e);
		}

		// execute kubectl in container

		var container = docker.getK3sContainer();
		if (container.isEmpty()) {
			throw new MojoExecutionException("Container not found");
		}

		log.info("Execute: {}", command);
		var callback = new DockerLogCallback(LoggerFactory.getLogger("io.kokuwa.maven.k3s.docker.kubectl"), streamLogs);
		docker.exec("kubectl", container.get(), cmd -> cmd
				.withCmd("/bin/sh", "-c", command)
				.withWorkingDir("/k3s/manifests")
				.withEnv(List.of("KUBECONFIG=/k3s/kubeconfig.yaml")), callback);

		// wait for pods to be ready

		var kubernetes = getKubernetesClient();
		Await.await("k3s pods ready").timeout(Duration.ofSeconds(podTimeout)).until(kubernetes::isPodsReady);
		log.debug("k3s pods ready");

		// wait for hostports to be ready

		Await.await("host ports in use").timeout(Duration.ofSeconds(podTimeout)).until(() -> isPortsBound(kubernetes));
		log.debug("host ports in use");
	}

	private boolean isPortsBound(Kubernetes kubernetes) throws MojoExecutionException, ApiException {
		var ports = kubernetes.getHostPorts();
		log.trace("Check ports: {}", ports);
		for (var port : ports) {
			if (isTcpPortAvailable(port)) {
				log.trace("Port {} is available.", port);
				return false;
			}
		}
		return true;
	}

	public boolean isTcpPortAvailable(int port) {
		try (var socket = new Socket()) {
			socket.connect(new InetSocketAddress(InetAddress.getLocalHost(), port), 20);
			return false;
		} catch (Throwable e) {
			return true;
		}
	}
}
