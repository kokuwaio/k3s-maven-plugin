package io.kokuwa.maven.k3s.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import io.kokuwa.maven.k3s.test.AbstractTest;
import io.kokuwa.maven.k3s.util.Docker.Container;

/**
 * Test for {@link Docker}.
 *
 * @author stephan.schnabel@posteo.de
 */
@DisplayName("util: docker")
public class DockerTest extends AbstractTest {

	@DisplayName("volume handling")
	@Test
	void volume() throws MojoExecutionException {

		assertFalse(docker.getVolume(DEFAUL_TASK_TIMEOUT).isPresent(), "volume found before testing");
		docker.removeVolume(DEFAUL_TASK_TIMEOUT);

		docker.createVolume(DEFAUL_TASK_TIMEOUT);
		assertTrue(docker.getVolume(DEFAUL_TASK_TIMEOUT).isPresent(), "volume missing after creating");

		docker.removeVolume(DEFAUL_TASK_TIMEOUT);
		assertFalse(docker.getVolume(DEFAUL_TASK_TIMEOUT).isPresent(), "volume found after removing");
	}

	@DisplayName("container handling")
	@Test
	void container(Logger log) throws MojoExecutionException {

		assertFalse(docker.getContainer(DEFAUL_TASK_TIMEOUT).isPresent(), "container found before testing");
		docker.removeContainer(DEFAUL_TASK_TIMEOUT);

		var ports = List.of("9001:9001", "9002:9002");
		docker.createVolume(DEFAUL_TASK_TIMEOUT);
		docker.createContainer("rancher/k3s", ports, List.of("server"), null, DEFAUL_TASK_TIMEOUT);
		assertTrue(docker.getContainer(DEFAUL_TASK_TIMEOUT).isPresent(), "container not found after creating");
		assertTrue(docker.getContainer(DEFAUL_TASK_TIMEOUT).map(Container::isRunning).orElse(null));
		docker.waitForLog(Await.await(log, "k3s"), o -> o.stream().anyMatch(l -> l.contains("k3s is up and running")),
				DEFAUL_TASK_TIMEOUT);

		docker.removeContainer(DEFAUL_TASK_TIMEOUT);
		assertFalse(docker.getContainer(DEFAUL_TASK_TIMEOUT).isPresent(), "container found after removing");
	}

	@DisplayName("image handling")
	@Test
	void image() throws MojoExecutionException {

		assertFalse(docker.getImage(helloWorld(), DEFAUL_TASK_TIMEOUT).isPresent(), "image found before testing");
		docker.removeImage(helloWorld(), DEFAUL_TASK_TIMEOUT);

		docker.pullImage(helloWorld(), Duration.ofSeconds(30));
		assertTrue(docker.getImage(helloWorld(), DEFAUL_TASK_TIMEOUT).isPresent(), "image missing after pulling");

		docker.removeImage(helloWorld(), DEFAUL_TASK_TIMEOUT);
		assertFalse(docker.getImage(helloWorld(), DEFAUL_TASK_TIMEOUT).isPresent(), "image found after removing");
	}

	@DisplayName("normalizeImage()")
	@Test
	void normalizeImage() {

		BiConsumer<String, String> assertImage = (e, i) -> assertEquals(e, docker.normalizeImage(i), i);

		assertImage.accept("docker.io/library/hello:latest", "hello");
		assertImage.accept("docker.io/library/hello:latest", "hello:latest");
		assertImage.accept("docker.io/library/hello:0.1.23", "hello:0.1.23");
		assertImage.accept("docker.io/library/hello@sha256:XYZ", "hello@sha256:XYZ");

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

		docker.createVolume(DEFAUL_TASK_TIMEOUT);
		docker.createContainer("rancher/k3s", List.of(), List.of("server"), null, DEFAUL_TASK_TIMEOUT);

		// define test data

		var containerDir = Paths.get("/k3s-data");
		var sourceDir = Paths.get("target", "docker-copy", "source");
		var sourceFile = sourceDir.resolve("test.txt");
		var returnDir = Paths.get("target", "docker-copy", "return");
		var returnFile = sourceDir.resolve("test.txt");
		Files.createDirectories(sourceDir);
		Files.createDirectories(returnDir);

		// write initial file and copy to container

		var initialContent = UUID.randomUUID().toString();
		Files.deleteIfExists(sourceFile);
		Files.deleteIfExists(returnFile);
		Files.write(sourceFile, initialContent.toString().getBytes());
		docker.copyToContainer(sourceDir, containerDir.toString(), DEFAUL_TASK_TIMEOUT);
		docker.copyFromContainer(containerDir.toString(), returnDir, DEFAUL_TASK_TIMEOUT);
		assertEquals(initialContent, Files.readString(returnFile));

		// write changed file and copy to container

		var changedContent = UUID.randomUUID().toString();
		Files.deleteIfExists(sourceFile);
		Files.deleteIfExists(returnFile);
		Files.write(sourceFile, changedContent.toString().getBytes());
		docker.copyToContainer(sourceDir, containerDir.toString(), DEFAUL_TASK_TIMEOUT);
		docker.copyFromContainer(containerDir.toString(), returnDir, DEFAUL_TASK_TIMEOUT);
		assertEquals(changedContent, Files.readString(returnFile));
	}
}
