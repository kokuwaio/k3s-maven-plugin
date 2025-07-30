package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.test.AbstractTest;
import io.kokuwa.maven.k3s.test.LoggerCapturer;
import io.kokuwa.maven.k3s.util.Await;

/**
 * Test for {@link RunMojo}.
 *
 * @author stephan@schnabel.org
 */
@DisplayName("mojo: run")
public class RunMojoTest extends AbstractTest {

	@DisplayName("with skip")
	@Test
	void withSkip(RunMojo runMojo) throws MojoExecutionException {

		runMojo.setSkipRun(false);
		runMojo.setSkip(true);
		assertDoesNotThrow(runMojo::execute);

		runMojo.setSkipRun(true);
		runMojo.setSkip(false);
		assertDoesNotThrow(runMojo::execute);

		runMojo.setSkipRun(true);
		runMojo.setSkip(true);
		assertDoesNotThrow(runMojo::execute);

		assertFalse(docker.getContainer().isPresent());
	}

	@DisplayName("with fail on existing container")
	@Test
	void withFailIfExists(RunMojo runMojo) throws MojoExecutionException {
		runMojo.setFailIfExists(true);
		assertDoesNotThrow(runMojo::execute);
		assertTrue(runMojo.getMarker().consumeStarted(), "started marker expected");
		var expectedMessage = "Container with id '" + docker.getContainer().get().getId()
				+ "' found. Please remove that container or set 'k3s.failIfExists' to false.";
		var actualMessage = assertThrows(MojoExecutionException.class, runMojo::execute).getMessage();
		assertEquals(expectedMessage, actualMessage, "exception message");
		assertFalse(runMojo.getMarker().consumeStarted(), "no started marker expected");
	}

	@DisplayName("with fail on existing container that is stopped")
	@Test
	void withFailIfExistsStopped(RunMojo runMojo) throws MojoExecutionException {
		runMojo.setFailIfExists(true);
		assertDoesNotThrow(runMojo::execute);
		assertTrue(runMojo.getMarker().consumeStarted(), "started marker expected");
		docker.kill(docker.getContainer().get());
		var expectedMessage = "Container with id '" + docker.getContainer().get().getId()
				+ "' found. Please remove that container or set 'k3s.failIfExists' to false.";
		var actualMessage = assertThrows(MojoExecutionException.class, runMojo::execute).getMessage();
		assertEquals(expectedMessage, actualMessage, "exception message");
		assertFalse(runMojo.getMarker().consumeStarted(), "no started marker expected");
	}

	@DisplayName("with replace on existing container")
	@Test
	void withReplaceIfExists(RunMojo runMojo) throws MojoExecutionException {
		runMojo.setFailIfExists(false);
		runMojo.setReplaceIfExists(true);
		assertDoesNotThrow(runMojo::execute);
		assertTrue(runMojo.getMarker().consumeStarted(), "started marker expected");
		var containerBefore = docker.getContainer().orElseThrow();
		assertDoesNotThrow(runMojo::execute);
		assertTrue(runMojo.getMarker().consumeStarted(), "started marker expected");
		var containerAfter = docker.getContainer().orElseThrow();
		assertNotEquals(containerBefore.getId(), containerAfter.getId(), "container was not replaced");
	}

	@DisplayName("with replace on existing container that is stopped")
	@Test
	void withReplaceIfExistsStopped(RunMojo runMojo) throws MojoExecutionException {
		runMojo.setFailIfExists(false);
		runMojo.setReplaceIfExists(true);
		assertDoesNotThrow(runMojo::execute);
		assertTrue(runMojo.getMarker().consumeStarted(), "started marker expected");
		var containerBefore = docker.getContainer().orElseThrow();
		docker.kill(containerBefore);
		assertDoesNotThrow(runMojo::execute);
		var containerAfter = docker.getContainer().orElseThrow();
		assertNotEquals(containerBefore.getId(), containerAfter.getId(), "container was not replaced");
		assertTrue(runMojo.getMarker().consumeStarted());
	}

	@DisplayName("without fail on existing container")
	@Test
	void withoutFailIfExists(RunMojo runMojo) throws MojoExecutionException {
		runMojo.setFailIfExists(false);
		runMojo.setReplaceIfExists(false);
		assertDoesNotThrow(runMojo::execute);
		assertTrue(runMojo.getMarker().consumeStarted(), "started marker expected");
		var containerBefore = docker.getContainer().orElseThrow();
		assertDoesNotThrow(runMojo::execute);
		var containerAfter = docker.getContainer().orElseThrow();
		assertEquals(containerBefore.getId(), containerAfter.getId(), "container shouldn't be replaced");
		assertFalse(runMojo.getMarker().consumeStarted(), "no started marker expected");
	}

	@DisplayName("without fail on existing container that is stopped")
	@Test
	void withoutFailIfExistsStopped(RunMojo runMojo) throws MojoExecutionException {
		runMojo.setFailIfExists(false);
		runMojo.setReplaceIfExists(false);
		assertDoesNotThrow(runMojo::execute);
		assertTrue(runMojo.getMarker().consumeStarted(), "started marker expected");
		var containerBefore = docker.getContainer().orElseThrow();
		docker.kill(containerBefore);
		assertEquals("exited", docker.getContainer().orElseThrow().getState());
		assertDoesNotThrow(runMojo::execute);
		assertTrue(runMojo.getMarker().consumeStarted(), "started marker expected");
		var containerAfter = docker.getContainer().orElseThrow();
		assertEquals(containerBefore.getId(), containerAfter.getId(), "container shouldn't be replaced");
	}

	@DisplayName("with custom registries.yaml")
	@Test
	void withRegistries(RunMojo runMojo) throws MojoExecutionException {
		runMojo.setRegistries(new File("src/test/resources/registries.yaml"));
		assertFalse(Files.exists(Paths.get("target/k3s.yaml")), "k3s.yaml not found");
		assertDoesNotThrow(runMojo::execute);
		assertTrue(runMojo.getMarker().consumeStarted(), "started marker expected");
		assertTrue(Files.exists(Paths.get("target/k3s.yaml")), "k3s.yaml not found");
		docker.waitForLog(docker.getContainer().get(), Await.await(log, "registries.yaml used"), s -> s.stream()
				.anyMatch(l -> l.contains("Using private registry config file at /etc/rancher/k3s/registries.yaml")));
	}

	@DisplayName("with custom registries.yaml (missing)")
	@Test
	void withRegistriesMissing(RunMojo runMojo) throws MojoExecutionException {
		var file = new File("src/test/resources/nope.yaml");
		runMojo.setRegistries(file);
		var actualMessage = assertThrowsExactly(MojoExecutionException.class, runMojo::execute).getMessage();
		assertEquals("Registries file '" + file.getAbsolutePath() + "' not found.", actualMessage, "exception message");
		assertFalse(runMojo.getMarker().consumeStarted(), "no started marker expected");
	}

	@DisplayName("dns: skipped")
	@Test
	void checkDnsSkipped(RunMojo runMojo) {
		runMojo.setSkip(true);
		runMojo.setDnsResolverCheck(false);
		assertDoesNotThrow(runMojo::execute);
		assertEquals(List.of(), LoggerCapturer.getMessages());
	}

	@DisplayName("dns: success")
	@Test
	void checkDnsSuccess(RunMojo runMojo) {
		runMojo.setSkip(true);
		assertDoesNotThrow(runMojo::execute);
		assertEquals(List.of("DEBUG io.kokuwa.maven.k3s.mojo.RunMojo - DNS resolved "
				+ "k3s-maven-plugin.127.0.0.1.nip.io to 127.0.0.1."), LoggerCapturer.getMessages());
	}

	@DisplayName("dns: failure")
	@Test
	void checkDnsFailure(RunMojo runMojo) {
		runMojo.setSkip(true);
		runMojo.setDnsResolverDomain("nope.example.org");
		assertDoesNotThrow(runMojo::execute);
		assertEquals(List.of("WARN io.kokuwa.maven.k3s.mojo.RunMojo - "
				+ "DNS was unable to resolve nope.example.org. Custom domains may not work!"),
				LoggerCapturer.getMessages());
	}
}
