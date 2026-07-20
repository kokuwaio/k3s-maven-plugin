package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.test.AbstractTest;

/**
 * Test for {@link DebugMojo}.
 *
 * @author stephan@schnabel.org
 */
@DisplayName("mojo: debug")
@SuppressWarnings("resource")
public class DebugMojoTest extends AbstractTest {

	final Path output = Paths.get("target/k3s/debug");

	@BeforeEach
	void reset() {
		assertDoesNotThrow(() -> FileUtils.deleteDirectory(output.toFile()));
	}

	@DisplayName("with skip")
	@Test
	void withSkip(DebugMojo debugMojo) throws MojoExecutionException {
		assertFalse(docker.getContainer().isPresent());

		debugMojo.setSkipDebug(false);
		debugMojo.setSkip(true);
		assertDoesNotThrow(debugMojo::execute);

		debugMojo.setSkipDebug(true);
		debugMojo.setSkip(false);
		assertDoesNotThrow(debugMojo::execute);

		debugMojo.setSkipDebug(true);
		debugMojo.setSkip(true);
		assertDoesNotThrow(debugMojo::execute);
	}

	@DisplayName("without container")
	@Test
	void withoutContainer(DebugMojo debugMojo) {
		var exception = assertThrowsExactly(MojoExecutionException.class, debugMojo::execute, () -> "No container");
		assertEquals("No container found", exception.getMessage(), "Exception message invalid.");
	}

	@DisplayName("with debug empty k3s")
	@Test
	void withK3sEmpty(RunMojo runMojo, DebugMojo debugMojo) throws IOException {
		assertDoesNotThrow(runMojo::execute);
		assertDoesNotThrow(debugMojo::execute);

		assertTrue(Files.isRegularFile(output.resolve("k3s.log")), "k3s.log not found");
		assertTrue(Files.size(output.resolve("k3s.log")) > 0, "k3s.log empty");
		assertTrue(Files.isRegularFile(output.resolve("k3s.yaml")), "k3s.yaml not found");
		assertTrue(Files.size(output.resolve("k3s.yaml")) > 0, "k3s.yaml empty");
		assertTrue(Files.isDirectory(output.resolve("containers")), "containers not found");
		assertEquals(0, Files.list(output.resolve("containers")).count(), "no containersr expected");
	}

	@DisplayName("with debug with pod")
	@Test
	void withPod(RunMojo runMojo, ApplyMojo applyMojo, DebugMojo debugMojo) throws IOException {
		assertDoesNotThrow(runMojo::execute);
		assertDoesNotThrow(applyMojo::execute);
		assertDoesNotThrow(debugMojo::execute);

		assertTrue(Files.isRegularFile(output.resolve("k3s.log")), "k3s.log not found");
		assertTrue(Files.size(output.resolve("k3s.log")) > 0, "k3s.log empty");
		assertTrue(Files.isRegularFile(output.resolve("k3s.yaml")), "k3s.yaml not found");
		assertTrue(Files.size(output.resolve("k3s.yaml")) > 0, "k3s.yaml empty");
		assertTrue(Files.isDirectory(output.resolve("containers")), "containers not found");
		var containerlogs = Files.list(output.resolve("containers")).toList();
		assertEquals(1, containerlogs.size(), "only one echo container expected: " + containerlogs);
		var logName = containerlogs.get(0).getName(containerlogs.get(0).getNameCount() - 1).toString();
		var logText = Files.readString(containerlogs.get(0));
		assertTrue(logName.startsWith("echo_default_echo-"), "unexpected container: " + logName);
		assertTrue(logText.contains("Z stdout F Echo server listening on port 8080."), "log not found: " + logText);
	}
}
