package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import io.kokuwa.maven.k3s.test.AbstractTest;
import io.kokuwa.maven.k3s.test.LoggerCapturer;
import io.kokuwa.maven.k3s.util.Await;
import io.kokuwa.maven.k3s.util.Task;

/**
 * Test for {@link RunMojo}.
 *
 * @author stephan.schnabel@posteo.de
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

		assertFalse(docker.getContainer(DEFAUL_TASK_TIMEOUT).isPresent());
	}

	@DisplayName("with fail on existing container")
	@Test
	void withFailIfExists(RunMojo runMojo) throws MojoExecutionException {
		runMojo.setFailIfExists(true);
		assertDoesNotThrow(runMojo::execute);
		assertTrue(runMojo.getMarker().consumeStarted(), "started marker expected");
		var expectedMessage = "Container with id '" + docker.getContainer(DEFAUL_TASK_TIMEOUT).get().id
				+ "' found. Please remove that container or set 'k3s.failIfExists' to false.";
		var actualMessage = assertThrows(MojoExecutionException.class, runMojo::execute).getMessage();
		assertEquals(expectedMessage, actualMessage, "exception message");
		assertFalse(runMojo.getMarker().consumeStarted(), "no started marker expected");
	}

	@DisplayName("with fail on existing container that is stopped")
	@Test
	void withFailIfExistsStopped(RunMojo runMojo, Logger log) throws MojoExecutionException {
		runMojo.setFailIfExists(true);
		assertDoesNotThrow(runMojo::execute);
		assertTrue(runMojo.getMarker().consumeStarted(), "started marker expected");
		Task.of(log, DEFAUL_TASK_TIMEOUT, "docker", "stop", "k3s-maven-plugin").run();
		var expectedMessage = "Container with id '" + docker.getContainer(DEFAUL_TASK_TIMEOUT).get().id
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
		var containerBefore = docker.getContainer(DEFAUL_TASK_TIMEOUT).orElseThrow();
		assertDoesNotThrow(runMojo::execute);
		assertTrue(runMojo.getMarker().consumeStarted(), "started marker expected");
		var containerAfter = docker.getContainer(DEFAUL_TASK_TIMEOUT).orElseThrow();
		assertNotEquals(containerBefore.id, containerAfter.id, "container was not replaced");
	}

	@DisplayName("with replace on existing container that is stopped")
	@Test
	void withReplaceIfExistsStopped(RunMojo runMojo, Logger log) throws MojoExecutionException {
		runMojo.setFailIfExists(false);
		runMojo.setReplaceIfExists(true);
		assertDoesNotThrow(runMojo::execute);
		assertTrue(runMojo.getMarker().consumeStarted(), "started marker expected");
		var containerBefore = docker.getContainer(DEFAUL_TASK_TIMEOUT).orElseThrow();
		Task.of(log, DEFAUL_TASK_TIMEOUT, "docker", "stop", "k3s-maven-plugin").run();
		assertDoesNotThrow(runMojo::execute);
		var containerAfter = docker.getContainer(DEFAUL_TASK_TIMEOUT).orElseThrow();
		assertNotEquals(containerBefore.id, containerAfter.id, "container was not replaced");
		assertTrue(runMojo.getMarker().consumeStarted());
	}

	@DisplayName("without fail on existing container")
	@Test
	void withoutFailIfExists(RunMojo runMojo) throws MojoExecutionException {
		runMojo.setFailIfExists(false);
		runMojo.setReplaceIfExists(false);
		assertDoesNotThrow(runMojo::execute);
		assertTrue(runMojo.getMarker().consumeStarted(), "started marker expected");
		var containerBefore = docker.getContainer(DEFAUL_TASK_TIMEOUT).orElseThrow();
		assertDoesNotThrow(runMojo::execute);
		var containerAfter = docker.getContainer(DEFAUL_TASK_TIMEOUT).orElseThrow();
		assertEquals(containerBefore.id, containerAfter.id, "container shouldn't be replaced");
		assertFalse(runMojo.getMarker().consumeStarted(), "no started marker expected");
	}

	@DisplayName("without fail on existing container that is stopped")
	@Test
	void withoutFailIfExistsStopped(RunMojo runMojo, Logger log, ApplyMojo applyMojo) throws MojoExecutionException {
		runMojo.setFailIfExists(false);
		runMojo.setReplaceIfExists(false);
		assertDoesNotThrow(runMojo::execute);
		assertTrue(runMojo.getMarker().consumeStarted(), "started marker expected");
		var containerBefore = docker.getContainer(DEFAUL_TASK_TIMEOUT).orElseThrow();
		Task.of(log, DEFAUL_TASK_TIMEOUT, "docker", "stop", "k3s-maven-plugin").run();
		assertDoesNotThrow(runMojo::execute);
		assertTrue(runMojo.getMarker().consumeStarted(), "started marker expected");
		assertDoesNotThrow(applyMojo::execute);
		var containerAfter = docker.getContainer(DEFAUL_TASK_TIMEOUT).orElseThrow();
		assertEquals(containerBefore.id, containerAfter.id, "container shouldn't be replaced");
	}

	@DisplayName("with custom registries.yaml")
	@Test
	void withRegistries(RunMojo runMojo, Logger log) throws MojoExecutionException {
		runMojo.setRegistries(new File("src/test/resources/registries.yaml"));
		assertDoesNotThrow(runMojo::execute);
		assertTrue(runMojo.getMarker().consumeStarted(), "started marker expected");
		docker.waitForLog(Await.await(log, "registries.yaml used"), logs -> logs.stream()
				.anyMatch(l -> l.contains("Using private registry config file at /etc/rancher/k3s/registries.yaml")),
				DEFAUL_TASK_TIMEOUT);
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
		assertEquals(
				List.of("DEBUG DNS resolved k3s-maven-plugin.127.0.0.1.nip.io to 127.0.0.1."),
				LoggerCapturer.getMessages());
	}

	@DisplayName("dns: failure")
	@Test
	void checkDnsFailure(RunMojo runMojo) {
		runMojo.setSkip(true);
		runMojo.setDnsResolverDomain("nope.example.org");
		assertDoesNotThrow(runMojo::execute);
		assertEquals(
				List.of("WARN DNS was unable to resolve nope.example.org. Custom domains may not work!"),
				LoggerCapturer.getMessages());
	}
}
