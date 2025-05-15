package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.test.AbstractTest;

/**
 * Test for {@link RestartMojo}.
 *
 * @author stephan@schnabel.org
 */
@DisplayName("mojo: restart")
public class RestartMojoTest extends AbstractTest {

	@DisplayName("with skip")
	@Test
	void withSkip(RestartMojo restartMojo) {

		restartMojo.setSkipRestart(false);
		restartMojo.setSkip(true);
		assertDoesNotThrow(restartMojo::execute);

		restartMojo.setSkipRestart(true);
		restartMojo.setSkip(false);
		assertDoesNotThrow(restartMojo::execute);

		restartMojo.setSkipRestart(true);
		restartMojo.setSkip(true);
		assertDoesNotThrow(restartMojo::execute);
	}

	@DisplayName("without container")
	@Test
	void withoutContainer(RestartMojo restartMojo) {
		restartMojo.setResources(List.of("pod/nope"));
		assertThrowsExactly(MojoExecutionException.class, restartMojo::execute, () -> "No k3s container found");
	}

	@DisplayName("without resources")
	@Test
	void withoutResources(RestartMojo restartMojo) {
		assertDoesNotThrow(restartMojo::execute);
	}

	@DisplayName("without invalid resource")
	@Test
	void invalid(RunMojo runMojo, RestartMojo restartMojo) {
		assertDoesNotThrow(runMojo::execute);
		assertTrue(assertDoesNotThrow(runMojo.getMarker()::consumeStarted), "started marker expected");
		restartMojo.setResources(List.of("pod/nope"));
		assertThrowsExactly(MojoExecutionException.class, restartMojo::execute, () -> "No k3s container found");
		restartMojo.setResources(List.of("deployment/nope/nope"));
		assertThrowsExactly(MojoExecutionException.class, restartMojo::execute, () -> "No k3s container found");
	}

	@DisplayName("with statefulset and started")
	@Test
	void statefulset(RunMojo runMojo, ApplyMojo applyMojo, RestartMojo restartMojo) {

		applyMojo.setSubdir("statefulset");
		restartMojo.setResources(List.of("statefulset/echo"));
		assertDoesNotThrow(runMojo::execute);
		assertTrue(assertDoesNotThrow(runMojo.getMarker()::consumeStarted), "started marker expected");
		assertDoesNotThrow(applyMojo::execute);

		// with restart mojo should take some time

		var started = System.currentTimeMillis();
		assertDoesNotThrow(restartMojo::execute);
		var duration = System.currentTimeMillis() - started;
		assertTrue(duration > 100, "restart did not happend");
	}

	@DisplayName("with not existing resource")
	@Test
	void notExisting(RunMojo runMojo, RestartMojo restartMojo) {
		restartMojo.setResources(List.of("statefulset/echo"));
		assertDoesNotThrow(runMojo::execute);
		assertTrue(assertDoesNotThrow(runMojo.getMarker()::consumeStarted), "started marker expected");
		assertThrowsExactly(MojoExecutionException.class, restartMojo::execute, () -> "Failed to restart resources");
	}

	@DisplayName("with marker start")
	@Test
	void withMarker(RunMojo runMojo, ApplyMojo applyMojo, RestartMojo restartMojo) {

		applyMojo.setSubdir("statefulset");
		restartMojo.setResources(List.of("statefulset/echo"));
		assertDoesNotThrow(runMojo::execute);
		assertDoesNotThrow(applyMojo::execute);

		// without restart mojo should immediately return

		var started = System.currentTimeMillis();
		assertDoesNotThrow(restartMojo::execute);
		var duration = System.currentTimeMillis() - started;
		assertTrue(duration < 10, "restart did happend");
		assertFalse(assertDoesNotThrow(runMojo.getMarker()::consumeStarted), "no started marker expected");
	}

}
