package io.kokuwa.maven.k3s.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.apache.maven.plugin.MojoExecutionException;
import org.slf4j.Logger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Wrapper for docker commands.
 *
 * @author stephan.schnabel@posteo.de
 * @since 1.0.0
 */
public class Docker {

	private final ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
	private final String containerName;
	private final String volumeName;
	private final Logger log;
	private final Duration timeout;

	public Docker(String containerName, String volumeName, Logger log, Duration timeout) {
		this.containerName = containerName;
		this.volumeName = volumeName;
		this.log = log;
		this.timeout = timeout;
	}

	public Optional<Container> getContainer() throws MojoExecutionException {
		return Task
				.of(log, timeout, "docker", "container", "ls", "--all", "--filter=name=" + containerName,
						"--format={{json .}}")
				.run().stream()
				.map(output -> readValue(Container.class, output))
				.filter(container -> containerName.equals(container.name))
				.findAny();
	}

	public void createContainer(String image, List<String> ports, List<String> k3s, Path registries)
			throws MojoExecutionException {
		var command = new ArrayList<String>();
		command.add("docker");
		command.add("run");
		command.add("--name=" + containerName);
		command.add("--privileged");
		command.add("--detach");
		command.add("--volume=" + volumeName + ":/var/lib/rancher/k3s/agent");
		if (registries != null) {
			command.add("--volume=" + registries + ":/etc/rancher/k3s/registries.yaml");
		}
		ports.stream().map(port -> "--publish=" + port).forEach(command::add);
		command.add(image);
		command.addAll(k3s);
		Task.of(log, timeout, command).run();
	}

	public void startContainer() throws MojoExecutionException {
		Task.of(log, timeout, "docker", "start", containerName).run();
	}

	public void removeContainer() throws MojoExecutionException {
		Task.of(log, timeout, "docker", "rm", containerName, "--force", "--volumes").run();
	}

	public void copyFromContainer(String source, Path destination) throws MojoExecutionException {
		Task.of(log, timeout, "docker", "cp", containerName + ":" + source, destination.toString()).run();
	}

	public void copyToContainer(Path source, String destination, Duration copyTimeout) throws MojoExecutionException {
		// suffix directories with '/.', see https://docs.docker.com/engine/reference/commandline/cp/#description
		var sourceString = Files.isDirectory(source) ? source + File.separator + "." : source.toString();
		Task.of(log, copyTimeout, "docker", "cp", sourceString, containerName + ":" + destination).run();
	}

	public void waitForLog(Await await, Function<List<String>, Boolean> checker) throws MojoExecutionException {
		var process = Task.of(log, Duration.ofHours(1), "docker", "logs", containerName, "--follow").start();
		await.onTimeout(() -> process.output().forEach(log::warn)).until(() -> process.output(), checker);
		process.close();
	}

	public List<String> exec(String... commands) throws MojoExecutionException {
		return exec(timeout, commands);
	}

	public List<String> exec(Duration execTimeout, String... commands) throws MojoExecutionException {
		return exec(execTimeout, List.of(commands));
	}

	public List<String> exec(Duration execTimeout, List<String> commands) throws MojoExecutionException {
		return execWithoutVerify(execTimeout, commands).verify().output();
	}

	public Task execWithoutVerify(List<String> commands) throws MojoExecutionException {
		return execWithoutVerify(timeout, commands);
	}

	public Task execWithoutVerify(Duration execTimeout, List<String> commands) throws MojoExecutionException {
		var command = new ArrayList<String>();
		command.add("docker");
		command.add("exec");
		command.add(containerName);
		command.addAll(commands);
		return Task.of(log, execTimeout, command).start().waitFor();
	}

	// volume

	public boolean isVolumePresent() throws MojoExecutionException {
		return Task.of(log, timeout, "docker", "volume", "ls", "--filter=name=" + volumeName, "--format={{json .}}")
				.run().stream()
				.map(output -> readValue(ContainerVolume.class, output))
				.filter(volume -> volumeName.equals(volume.name))
				.findAny().isPresent();
	}

	public void createVolume() throws MojoExecutionException {
		Task.of(log, timeout, "docker", "volume", "create", volumeName).run();
	}

	public void removeVolume() throws MojoExecutionException {
		Task.of(log, timeout, "docker", "volume", "rm", volumeName, "--force").run();
	}

	// images

	public String normalizeImage(String image) {

		var newImageName = image;

		if (!image.contains("@sha256:") && !image.contains(":")) {
			newImageName += ":latest";
		}

		var slashIndex = image.indexOf('/');
		if (slashIndex == -1) {
			newImageName = "docker.io/library/" + newImageName;
		} else if (!image.substring(0, slashIndex).contains(".")) {
			newImageName = "docker.io/" + newImageName;
		}

		return newImageName;
	}

	public Optional<ContainerImage> findImage(String image) throws MojoExecutionException {
		var task = Task.of(log, timeout, "docker", "image", "inspect", image, "--format={{json .}}").start().waitFor();
		return task.exitCode() == 0
				? task.output().stream().map(output -> readValue(ContainerImage.class, output)).findAny()
				: Optional.empty();
	}

	public void pullImage(String image, Duration pullTimeout) throws MojoExecutionException {
		Task.of(log, pullTimeout, "docker", "image", "pull", "--quiet", image).run();
	}

	public void saveImage(String image, Path path, Duration saveTimeout) throws MojoExecutionException {
		Task.of(log, saveTimeout, "docker", "image", "save", "--output=" + path, image).run();
	}

	public void removeImage(String image) throws MojoExecutionException {
		Task.of(log, timeout, "docker", "image", "rm", "--force", image).run();
	}

	// internal

	private <T> T readValue(Class<T> type, String output) {
		try {
			return mapper.readValue(output, type);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to parse line to " + type.getName() + ": " + output, e);
		}
	}

	// models

	/**
	 * Response of <code>docker ps --format=json</code>.
	 *
	 * @see <a href="https://docs.docker.com/engine/reference/commandline/volume_inspect/#format">docker v inspect</a>
	 */
	public static class Container {

		private final String id;
		private final String name;
		private final ContainerStatus state;

		@JsonCreator
		public Container(
				@JsonProperty("ID") String id,
				@JsonProperty("Names") String name,
				@JsonProperty("State") ContainerStatus state) {
			this.id = id;
			this.name = name;
			this.state = state;
		}

		public boolean isRunning() {
			return state == ContainerStatus.running || state == ContainerStatus.restarting;
		}

		public String getId() {
			return id;
		}
	}

	/**
	 * Available status of a container.
	 *
	 * @see <a href="https://docs.docker.com/engine/reference/commandline/ps/#status">docker ps</a>
	 */
	public enum ContainerStatus {

		created,
		restarting,
		running,
		removing,
		paused,
		exited,
		dead
	}

	/**
	 * Response of <code>docker volume ls --format=json</code>.
	 *
	 * @see <a href="https://docs.docker.com/engine/reference/commandline/volume_inspect/#format">docker v inspect</a>
	 */
	public static class ContainerVolume {

		private final String name;

		@JsonCreator
		public ContainerVolume(@JsonProperty("Name") String name) {
			this.name = name;
		}
	}

	/**
	 * Response of <code>docker image inspect --format=json</code>.
	 *
	 * @see <a href="https://docs.docker.com/engine/reference/commandline/image_inspect/">docker image inspect</a>
	 */
	public static class ContainerImage {

		private final String id;
		private final String created;
		private final Long size;
		private final List<String> repoDigests;
		private final Map<String, Object> rootFs;
		private final Map<String, Object> metadata;

		@JsonCreator
		public ContainerImage(
				@JsonProperty("Id") String id,
				@JsonProperty("Created") String created,
				@JsonProperty("Size") Long size,
				@JsonProperty("RepoDigests") List<String> repoDigests,
				@JsonProperty("RootFS") Map<String, Object> rootFs,
				@JsonProperty("Metadata") Map<String, Object> metadata) {
			this.id = id;
			this.created = created;
			this.size = size;
			this.repoDigests = repoDigests;
			this.rootFs = rootFs;
			this.metadata = metadata;
		}

		public String getDigest() {
			return repoDigests.stream()
					.filter(i -> i.contains("@sha256"))
					.map(i -> i.substring(i.indexOf("@sha256:") + 8))
					.findFirst()
					.orElse(id + "_" + size + "_" + created + "_" + rootFs.hashCode() + "_" + metadata.hashCode());
		}
	}
}
