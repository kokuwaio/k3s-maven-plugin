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
		var message = "Container with id '" + docker.getContainer().get().id
				+ "' found. Please remove that container or set 'k3s.failIfExists' to false.";
		assertThrowsExactly(MojoExecutionException.class, runMojo::execute, () -> message);
	}

	@DisplayName("with replace on existing container")
	@Test
	void withReplaceIfExists(RunMojo runMojo) throws MojoExecutionException {
		runMojo.setFailIfExists(false);
		runMojo.setReplaceIfExists(true);
		assertDoesNotThrow(runMojo::execute);
		var containerBefore = docker.getContainer().orElseThrow();
		assertDoesNotThrow(runMojo::execute);
		var containerAfter = docker.getContainer().orElseThrow();
		assertNotEquals(containerBefore.id, containerAfter.id, "container was not replaced");
	}

	@DisplayName("without fail on existing container")
	@Test
	void withoutFailIfExists(RunMojo runMojo) throws MojoExecutionException {
		runMojo.setFailIfExists(false);
		runMojo.setReplaceIfExists(false);
		assertDoesNotThrow(runMojo::execute);
		var containerBefore = docker.getContainer().orElseThrow();
		assertDoesNotThrow(runMojo::execute);
		var containerAfter = docker.getContainer().orElseThrow();
		assertEquals(containerBefore.id, containerAfter.id, "container shouldn't be replaced");
	}
}
