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

	final Image image = helloWorld();
	final Image imageWithoutTag = Image.of(image.registry() + "/" + image.repository() + "@" + image.digest());
	final Image imageWithoutDigest = Image.of(image.registry() + "/" + image.repository() + ":" + image.tag());
	final Image imageWithoutTagAndDigest = Image.of(image.registry() + "/" + image.repository());
	final Image imageInvalidDigest = Image
			.of(image.registry() + "/" + image.repository() + image.tag() + "@sha256:1asdasd");

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
		docker.pullImage(Image.of("rancher/k3s"), Duration.ofMinutes(10));
		docker.createVolume();
		docker.createContainer(Image.of("rancher/k3s"), null, List.of("6443:6443"), List.of("server"));
		var container = docker.getContainer().orElse(null);
		assertNotNull(container, "container not found after creating");
		docker.start(container);
		container = docker.getContainer().get();
		assertTrue(docker.isRunning(container), "container should run, status: " + container.getStatus());
		docker.remove(container);
		assertFalse(docker.getContainer().isPresent(), "container found after removing");
	}

	@DisplayName("findImage() with tag/digest")
	@Test
	void pullImageWithTagAndDigest() throws MojoExecutionException {
		docker.pullImage(image, Duration.ofSeconds(30));
		assertEquals(image, docker.findImage(image).get(), "image missing after pulling");
		assertEquals(image, docker.findImage(imageWithoutTag).get(), "image missing without tag");
		assertEquals(image.digest(), docker.findImage(imageWithoutDigest).get().digest(),
				"image missing without digest");
		assertFalse(docker.findImage(imageInvalidDigest).isPresent(), "image missing because invalid digest");
	}

	@DisplayName("findImage() without tag")
	@Test
	void pullImageWithoutTag() throws MojoExecutionException {
		docker.pullImage(imageWithoutTag, Duration.ofSeconds(30));
		assertFalse(docker.findImage(image).isPresent(), "image missing after pulling");
		assertEquals(image.digest(), docker.findImage(imageWithoutTag).get().digest(), "image missing without tag");
		assertFalse(docker.findImage(imageWithoutDigest).isPresent(), "image missing without digest");
		assertFalse(docker.findImage(imageInvalidDigest).isPresent(), "image missing because invalid digest");
	}

	@DisplayName("findImage() without digest")
	@Test
	void pullImageWithoutDigest() throws MojoExecutionException {
		docker.pullImage(imageWithoutDigest, Duration.ofSeconds(30));
		assertEquals(image, docker.findImage(image).get(), "image missing after pulling");
		assertEquals(image, docker.findImage(imageWithoutTag).get(), "image missing without tag");
		assertTrue(docker.findImage(imageWithoutDigest).isPresent(), "image missing without digest");
		assertFalse(docker.findImage(imageInvalidDigest).isPresent(), "image missing because invalid digest");
	}

	@DisplayName("pullImage() without tag/digest")
	@Test
	void pullImageWithoutTagAndDigest() throws MojoExecutionException {
		docker.pullImage(imageWithoutDigest, Duration.ofSeconds(30));
		assertEquals(image, docker.findImage(image).get(), "image missing after pulling");
		assertEquals(image, docker.findImage(imageWithoutTag).get(), "image missing without tag");
		assertTrue(docker.findImage(imageWithoutDigest).isPresent(), "image missing without digest");
		assertFalse(docker.findImage(imageInvalidDigest).isPresent(), "image missing because invalid digest");
	}

	@DisplayName("copy()")
	@Test
	void copy() throws MojoExecutionException, IOException {
		// start container

		docker.createVolume();
		docker.pullImage(Image.of("rancher/k3s"), Duration.ofMinutes(10));
		var container = docker.createContainer(Image.of("rancher/k3s"), null, List.of(), List.of("server"));
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
