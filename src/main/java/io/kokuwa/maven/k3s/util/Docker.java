package io.kokuwa.maven.k3s.util;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

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
	private final Log log;

	public Docker(String containerName, String volumeName, Log log) {
		this.containerName = containerName;
		this.volumeName = volumeName;
		this.log = log;
	}

	public Optional<Container> getContainer() throws MojoExecutionException {
		return Task.of(log, "docker", "container", "ls", "--filter=name=" + containerName, "--format={{json .}}")
				.run().stream()
				.map(output -> readValue(Container.class, output))
				.filter(container -> containerName.equals(container.name))
				.findAny();
	}

	public void createContainer(String image, List<String> ports, List<String> k3s) throws MojoExecutionException {
		var command = new ArrayList<String>();
		command.add("docker");
		command.add("run");
		command.add("--name=" + containerName);
		command.add("--privileged");
		command.add("--detach");
		command.add("--volume=" + volumeName + ":/var/lib/rancher/k3s/agent");
		ports.stream().map(port -> "--publish=" + port).forEach(command::add);
		command.add(image);
		command.addAll(k3s);
		Task.of(log, command).run();
	}

	public void removeContainer() throws MojoExecutionException {
		Task.of(log, "docker", "rm", containerName, "--force", "--volumes").run();
	}

	public void copyFromContainer(Path source, Path destination) throws MojoExecutionException {
		Task.of(log, "docker", "cp", containerName + ":" + source, destination.toString()).run();
	}

	public void copyToContainer(Path source, Path destination) throws MojoExecutionException {
		Task.of(log, "docker", "cp", source.toString(), containerName + ":" + destination).run();
	}

	public void waitForLog(Await await, Function<List<String>, Boolean> checker) throws MojoExecutionException {
		var process = Task.of(log, "docker", "logs", containerName, "--follow").timeout(Duration.ofHours(1)).start();
		await.onTimeout(() -> process.output().forEach(log::warn)).until(() -> process.output(), checker);
		process.close();
	}

	public List<String> exec(String... commands) throws MojoExecutionException {
		return execWithoutVerify(List.of(commands)).verify().output();
	}

	public List<String> exec(Duration timeout, String... commands) throws MojoExecutionException {
		return execWithoutVerify(timeout, List.of(commands)).verify().output();
	}

	public Task execWithoutVerify(List<String> commands) throws MojoExecutionException {
		return execWithoutVerify(null, commands);
	}

	public Task execWithoutVerify(Duration timeout, List<String> commands) throws MojoExecutionException {
		var command = new ArrayList<String>();
		command.add("docker");
		command.add("exec");
		command.add(containerName);
		command.addAll(commands);
		var task = Task.of(log, command);
		if (timeout != null) {
			task.timeout(timeout);
		}
		return task.start().waitFor();
	}

	// volume

	public Optional<ContainerVolume> getVolume() throws MojoExecutionException {
		return Task.of(log, "docker", "volume", "ls", "--filter=name=" + volumeName, "--format={{json .}}")
				.run().stream()
				.map(output -> readValue(ContainerVolume.class, output))
				.filter(volume -> volumeName.equals(volume.name))
				.findAny();
	}

	public void createVolume() throws MojoExecutionException {
		Task.of(log, "docker", "volume", "create", volumeName).run();
	}

	public void removeVolume() throws MojoExecutionException {
		Task.of(log, "docker", "volume", "rm", volumeName, "--force").run();
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

	public Optional<ContainerImage> getImage(String image) throws MojoExecutionException {
		return Task.of(log, "docker", "image", "ls", "--filter=reference=" + image, "--format={{json .}}")
				.run().stream()
				.map(output -> readValue(ContainerImage.class, output))
				.filter(i -> normalizeImage(image).equals(normalizeImage(i.repository + ":" + i.tag)))
				.findAny();
	}

	public void pullImage(String image, Duration timeout) throws MojoExecutionException {
		Task.of(log, "docker", "image", "pull", "--quiet", image).timeout(timeout).run();
	}

	public void saveImage(String image, Path path) throws MojoExecutionException {
		Task.of(log, "docker", "image", "save", "--output=" + path, image).run();
	}

	public void removeImage(String image) throws MojoExecutionException {
		Task.of(log, "docker", "image", "rm", "--force", image).run();
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

		public final String id;
		public final String name;
		public final ContainerStatus state;

		@JsonCreator
		public Container(@JsonProperty("ID") String id, @JsonProperty("Names") String name,
				@JsonProperty("State") ContainerStatus state) {
			this.id = id;
			this.name = name;
			this.state = state;
		}

		public boolean isRunnding() {
			return state == ContainerStatus.running || state == ContainerStatus.restarting;
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

		public final String name;
		public final String mountpoint;

		@JsonCreator
		public ContainerVolume(@JsonProperty("Name") String name, @JsonProperty("Mountpoint") String mountpoint) {
			this.name = name;
			this.mountpoint = mountpoint;
		}
	}

	/**
	 * Response of <code>docker image ls --format=json</code>.
	 *
	 * @see <a href="https://docs.docker.com/engine/reference/commandline/image_ls/">docker image ls</a>
	 */
	public static class ContainerImage {

		public final String repository;
		public final String tag;

		@JsonCreator
		public ContainerImage(@JsonProperty("Repository") String repository, @JsonProperty("Tag") String tag) {
			this.repository = repository;
			this.tag = tag;
		}
	}
}
