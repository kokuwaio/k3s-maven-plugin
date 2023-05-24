package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.test.AbstractTest;

/**
 * Test for {@link RunMojo}.
 *
 * @author stephan.schnabel@posteo.de
 */
@DisplayName("mojo: run")
public class RunMojoTest extends AbstractTest {

	@DisplayName("with skip")
	@Test
	void withSkip(RunMojo runMojo) {
		assertDoesNotThrow(() -> runMojo.setSkipRun(false).setSkip(true).execute());
		assertDoesNotThrow(() -> runMojo.setSkipRun(true).setSkip(false).execute());
		assertDoesNotThrow(() -> runMojo.setSkipRun(true).setSkip(true).execute());
		assertFalse(docker.getContainer().isPresent());
	}

	@DisplayName("with fail on existing container")
	@Test
	void withFailIfExists(RunMojo runMojo) {
		runMojo.setFailIfExists(true);
		assertDoesNotThrow(runMojo::execute);
		var message = "Container with id '" + docker.getContainer().orElseThrow().getId()
				+ "' found. Please remove that container or set 'k3s.failIfExists' to false.";
		assertThrowsExactly(MojoExecutionException.class, runMojo::execute, () -> message);
	}

	@DisplayName("with replace on existing container")
	@Test
	void withReplaceIfExists(RunMojo runMojo) {
		runMojo.setFailIfExists(false).setReplaceIfExists(true);
		assertDoesNotThrow(runMojo::execute);
		var containerBefore = docker.getContainer().orElseThrow();
		assertDoesNotThrow(runMojo::execute);
		var containerAfter = docker.getContainer().orElseThrow();
		assertNotEquals(containerBefore.getId(), containerAfter.getId(), "container was not replaced");
	}

	@DisplayName("without fail on existing container")
	@Test
	void withoutFailIfExists(RunMojo runMojo) {
		runMojo.setFailIfExists(false).setReplaceIfExists(false);
		assertDoesNotThrow(runMojo::execute);
		var containerBefore = docker.getContainer().orElseThrow();
		assertDoesNotThrow(runMojo::execute);
		var containerAfter = docker.getContainer().orElseThrow();
		assertEquals(containerBefore.getId(), containerAfter.getId(), "container shouldn't be replaced");
	}
}
