package io.kokuwa.maven.k3s.mojo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

import com.github.dockerjava.api.model.Container;
import io.kokuwa.maven.k3s.util.Await;
import io.kokuwa.maven.k3s.util.DockerLogCallback;

/**
 * Base for Mojos that reference the k3s image.
 *
 * @author stephan@schnabel.org
 * @since 2.2.0
 */
public abstract class K3sDebugMojo extends K3sMojo {

	/**
	 * Write debug data to stdout?
	 *
	 * @since 2.2.0
	 */
	@Parameter(property = "k3s.debugToStdout", defaultValue = "false")
	private boolean debugToStdout;

	/**
	 * Path where debug data should by written to.
	 *
	 * @since 2.2.0
	 */
	@Parameter(property = "k3s.debugDirectory", defaultValue = "${project.build.directory}/k3s/debug")
	private Path debugDirectory;

	@SuppressWarnings("resource")
	void handleDebugInfos(Container container) throws MojoExecutionException {
		var containers = debugDirectory.resolve("containers");
		try {
			Files.createDirectories(debugDirectory);
			Files.createDirectories(containers);
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to prepare debug directory", e);
		}

		var k3sLogFile = debugDirectory.resolve("k3s.log");
		log.info("Collect k3s docker logs to {}", k3sLogFile);
		var callback = getDocker().getClient()
				.logContainerCmd(container.getId())
				.withSince(0)
				.withStdOut(true)
				.withStdErr(true)
				.exec(new DockerLogCallback());
		Await.await(log, "Collect logs of k3s.").until(() -> callback.isCompleted());
		var k3sLogText = callback.messages.stream().collect(Collectors.joining("\n"));
		try {
			new FileWriter(k3sLogFile.toString()).write(k3sLogText);
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to collect k3s logs", e);
		}

		log.info("Collect container logs to {}", containers);
		for (var logName : getDocker().exec(container, "ls", "/var/log/containers")) {
			var realPath = getDocker().exec(container, "realpath", "/var/log/containers/" + logName).get(0);
			getDocker().copyFromContainer(container, realPath, containers);
			var source = containers.resolve(Paths.get(realPath).toFile().getName());
			var destination = containers.resolve(logName);
			log.debug("Copy container logs from {} to {}", source, destination);
			try {
				Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new MojoExecutionException(
						"Failed to move " + source + " to " + destination + ": " + e.getMessage(), e);
			}
		}

		var k3sManifests = debugDirectory.resolve("k3s.yaml");
		log.info("Collect manifests from k3s to {}", k3sManifests);
		getDocker().exec(container, "sh", "-c", "kubectl get all --all-namespaces --output=yaml > /tmp/k3s.yaml");
		getDocker().copyFromContainer(container, "/tmp/k3s.yaml", debugDirectory);

		if (debugToStdout) {
			try {
				log.warn("k3s manifests: \n\n{}\n\n", Files.readString(k3sManifests));
				log.warn("k3s logs: \n\n{}\n\n", Files.readString(k3sLogFile));
				var containerLogs = Files.list(debugDirectory.resolve("containers")).toList();
				if (containerLogs.isEmpty()) {
					log.warn("k3s container logs not found");
				} else {
					for (var containerLog : containerLogs) {
						log.warn("k3s container {}: \n\n{}\n\n", containerLog.getFileName(),
								Files.readString(containerLog));
					}
				}
			} catch (IOException e) {
				throw new MojoExecutionException("Failed to print debug logs", e);
			}
		}
	}

	// setter

	public void setDebugToStdout(boolean debugToStdout) {
		this.debugToStdout = debugToStdout;
	}

	public void setDebugDirectory(File debugDirectory) {
		this.debugDirectory = debugDirectory.toPath().toAbsolutePath();
	}
}
