package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.AbstractTest;

@DisplayName("mojo: create")
public class CreateMojoTest extends AbstractTest {

	@DisplayName("with skip")
	@Test
	void withSkip(CreateMojo mojo) {
		assertDoesNotThrow(() -> mojo.setSkip(true).execute());
	}

	@DisplayName("with fail on existing container")
	@Test
	void withFailIfExists(CreateMojo mojo) {
		assertDoesNotThrow(() -> mojo.execute());
		var msg = assertThrows(MojoExecutionException.class, () -> mojo.setFailIfExists(true).execute()).getMessage();
		assertAll("invalid message: " + msg,
				() -> assertTrue(msg.startsWith("Container with id '")),
				() -> assertTrue(msg.endsWith(
						"' found. Please remove that container or set 'k3s.failIfExists' to false.")));
	}

	@DisplayName("with replace on existing container")
	@Test
	void withReplaceIfExists(CreateMojo mojo) {
		assertDoesNotThrow(() -> mojo.execute());
		var containerBefore = docker.getContainer();
		assertDoesNotThrow(() -> mojo.setFailIfExists(false).setReplaceIfExists(true).execute());
		var containerAfter = docker.getContainer();
		assertNotEquals(containerBefore.get().getId(), containerAfter.get().getId(), "container was not replaced");
	}

	@DisplayName("without fail on existing container")
	@Test
	void withoutFailIfExists(CreateMojo mojo) {
		assertDoesNotThrow(() -> mojo.execute());
		var containerBefore = docker.getContainer();
		assertDoesNotThrow(() -> mojo.setFailIfExists(false).setReplaceIfExists(false).execute());
		var containerAfter = docker.getContainer();
		assertEquals(containerBefore.get().getId(), containerAfter.get().getId(), "container shouldn't be replaced");
	}
}
