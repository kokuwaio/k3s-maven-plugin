package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.AgentCacheMode;
import io.kokuwa.maven.k3s.test.AbstractTest;

/**
 * Test for {@link CreateMojo}.
 *
 * @author stephan.schnabel@posteo.de
 */
@DisplayName("mojo: create")
public class CreateMojoTest extends AbstractTest {

	@DisplayName("with skip")
	@Test
	void withSkip(CreateMojo createMojo) {
		assertDoesNotThrow(() -> createMojo.setSkipCreate(false).setSkip(true).execute());
		assertDoesNotThrow(() -> createMojo.setSkipCreate(true).setSkip(false).execute());
		assertDoesNotThrow(() -> createMojo.setSkipCreate(true).setSkip(true).execute());
		assertFalse(docker.getContainer().isPresent());
	}

	@DisplayName("with fail on existing container")
	@Test
	void withFailIfExists(CreateMojo createMojo) {
		createMojo.setFailIfExists(true);
		assertDoesNotThrow(createMojo::execute);
		var message = "Container with id '" + docker.getContainer().orElseThrow().getId()
				+ "' found. Please remove that container or set 'k3s.failIfExists' to false.";
		assertThrowsExactly(MojoExecutionException.class, createMojo::execute, () -> message);
	}

	@DisplayName("with replace on existing container")
	@Test
	void withReplaceIfExists(CreateMojo createMojo) {
		createMojo.setFailIfExists(false).setReplaceIfExists(true);
		assertDoesNotThrow(createMojo::execute);
		var containerBefore = docker.getContainer().orElseThrow();
		assertDoesNotThrow(createMojo::execute);
		var containerAfter = docker.getContainer().orElseThrow();
		assertNotEquals(containerBefore.getId(), containerAfter.getId(), "container was not replaced");
	}

	@DisplayName("without fail on existing container")
	@Test
	void withoutFailIfExists(CreateMojo createMojo) {
		createMojo.setFailIfExists(false).setReplaceIfExists(false);
		assertDoesNotThrow(createMojo::execute);
		var containerBefore = docker.getContainer().orElseThrow();
		assertDoesNotThrow(createMojo::execute);
		var containerAfter = docker.getContainer().orElseThrow();
		assertEquals(containerBefore.getId(), containerAfter.getId(), "container shouldn't be replaced");
	}

	@DisplayName("with agent cache NONE")
	@Test
	void withCacheNone(CreateMojo createMojo) {
		assertDoesNotThrow(() -> createMojo.setAgentCache(AgentCacheMode.NONE).execute());
	}

	@DisplayName("with agent cache VOLUME")
	@Test
	void withCacheVolume(CreateMojo createMojo) {
		assertDoesNotThrow(() -> createMojo.setAgentCache(AgentCacheMode.VOLUME).execute());
		assertTrue(docker.isVolumePresent());
		docker.getContainer().ifPresent(docker::removeContainer);
		docker.removeVolume();
	}

	@DisplayName("with agent cache HOST")
	@Test
	void withCacheHost(CreateMojo createMojo) {
		assertDoesNotThrow(() -> createMojo.setAgentCache(AgentCacheMode.HOST).execute());
	}
}
