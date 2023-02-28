package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
		assertTrue(msg.startsWith("Container with id '"), "invalid message: " + msg);
		assertTrue(msg.endsWith("' found. Please remove that container."), "invalid message: " + msg);
	}

	@DisplayName("without fail on existing container")
	@Test
	void withoutFailIfExists(CreateMojo mojo) {
		assertDoesNotThrow(() -> mojo.execute());
		assertDoesNotThrow(() -> mojo.setFailIfExists(false).execute());
	}
}
