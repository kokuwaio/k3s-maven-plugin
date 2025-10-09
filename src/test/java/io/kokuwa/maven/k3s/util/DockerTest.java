package io.kokuwa.maven.k3s.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.test.AbstractTest;

/**
 * Test for {@link Docker}.
 *
 * @author stephan@schnabel.org
 */
@DisplayName("util: docker")
public class DockerTest extends AbstractTest {

	@DisplayName("volume handling")
	@Test
	void volume() throws MojoExecutionException {

		assertFalse(docker.isVolumePresent(), "volume found before testing");
		docker.removeVolume();

		docker.createVolume();
		assertTrue(docker.isVolumePresent(), "volume missing after creating");

		docker.removeVolume();
		assertFalse(docker.isVolumePresent(), "volume found after removing");
	}

	@DisplayName("container handling")
	@Test
	void container() throws MojoExecutionException {
		assertFalse(docker.getContainer().isPresent(), "container found before testing");
		docker.pullImage("docker.io/rancher/k3s:latest", Duration.ofMinutes(10));
		docker.createVolume();
		docker.createContainer("docker.io/rancher/k3s:latest", null, List.of("6443:6443"), List.of("server"));
		var container = docker.getContainer().orElse(null);
		assertNotNull(container, "container not found after creating");
		docker.start(container);
		container = docker.getContainer().get();
		assertTrue(docker.isRunning(container), "container should run, status: " + container.getStatus());
		docker.remove(container);
		assertFalse(docker.getContainer().isPresent(), "container found after removing");
	}

	@DisplayName("image handling")
	@Test
	void image() throws MojoExecutionException {

		assertFalse(docker.findImage(helloWorld()).isPresent(), "image found before testing");
		docker.removeImage(helloWorld());

		docker.pullImage(helloWorld(), Duration.ofSeconds(30));
		assertTrue(docker.findImage(helloWorld()).isPresent(), "image missing after pulling");

		docker.removeImage(helloWorld());
		assertFalse(docker.findImage(helloWorld()).isPresent(), "image found after removing");
	}

	@DisplayName("normalizeImage()")
	@Test
	void normalizeImage() {

		BiConsumer<String, String> assertImage = (e, i) -> assertEquals(e, docker.normalizeImage(i), i);

		assertImage.accept("docker.io/library/hello:latest", "hello");
		assertImage.accept("docker.io/library/hello:latest", "hello:latest");
		assertImage.accept("docker.io/library/hello:0.1.23", "hello:0.1.23");
		assertImage.accept("docker.io/library/hello@sha256:XYZ", "hello@sha256:XYZ");

		assertImage.accept("docker.io/library/world:latest", "docker.io/world");
		assertImage.accept("docker.io/library/world:latest", "docker.io/world:latest");
		assertImage.accept("docker.io/library/world:0.1.23", "docker.io/world:0.1.23");
		assertImage.accept("docker.io/library/world@sha256:XYZ", "docker.io/world@sha256:XYZ");

		assertImage.accept("docker.io/hello/world:latest", "hello/world");
		assertImage.accept("docker.io/hello/world:latest", "hello/world:latest");
		assertImage.accept("docker.io/hello/world:0.1.23", "hello/world:0.1.23");
		assertImage.accept("docker.io/hello/world@sha256:XYZ", "hello/world@sha256:XYZ");

		assertImage.accept("docker.io/hello/world:latest", "docker.io/hello/world");
		assertImage.accept("docker.io/hello/world:latest", "docker.io/hello/world:latest");
		assertImage.accept("docker.io/hello/world:0.1.23", "docker.io/hello/world:0.1.23");
		assertImage.accept("docker.io/hello/world@sha256:XYZ", "docker.io/hello/world@sha256:XYZ");

		assertImage.accept("quay.io/hello/world:latest", "quay.io/hello/world");
		assertImage.accept("quay.io/hello/world:latest", "quay.io/hello/world:latest");
		assertImage.accept("quay.io/hello/world:0.1.23", "quay.io/hello/world:0.1.23");
		assertImage.accept("quay.io/hello/world@sha256:XYZ", "quay.io/hello/world@sha256:XYZ");
	}

	@DisplayName("copy()")
	@Test
	void copy() throws MojoExecutionException, IOException {

		// start container

		docker.createVolume();
		docker.pullImage("docker.io/rancher/k3s:latest", Duration.ofMinutes(10));
		var container = docker.createContainer("docker.io/rancher/k3s:latest", null, List.of(), List.of("server"));
		docker.start(container);

		// define test data

		var containerDir = Path.of("/k3s-data");
		var sourceDir = Path.of("target", "docker-copy", "source");
		var sourceFile = sourceDir.resolve("test.txt");
		var returnDir = Path.of("target", "docker-copy", "return");
		var returnFile = sourceDir.resolve("test.txt");
		Files.createDirectories(sourceDir);
		Files.createDirectories(returnDir);

		// write initial file and copy to container

		var initialContent = UUID.randomUUID().toString();
		Files.deleteIfExists(sourceFile);
		Files.deleteIfExists(returnFile);
		Files.write(sourceFile, initialContent.toString().getBytes());
		docker.copyToContainer(container, sourceDir, containerDir.toString());
		docker.copyFromContainer(container, containerDir.toString(), returnDir);
		assertEquals(initialContent, Files.readString(returnFile));

		// write changed file and copy to container

		var changedContent = UUID.randomUUID().toString();
		Files.deleteIfExists(sourceFile);
		Files.deleteIfExists(returnFile);
		Files.write(sourceFile, changedContent.toString().getBytes());
		docker.copyToContainer(container, sourceDir, containerDir.toString());
		docker.copyFromContainer(container, containerDir.toString(), returnDir);
		assertEquals(changedContent, Files.readString(returnFile));
	}
}
