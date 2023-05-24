package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.AgentCacheMode;
import io.kokuwa.maven.k3s.test.AbstractTest;

/**
 * Test for {@link RemoveMojo}.
 *
 * @author stephan.schnabel@posteo.de
 */
@DisplayName("mojo: rm")
public class RemoveMojoTest extends AbstractTest {

	@DisplayName("with skip")
	@Test
	void withSkip(RemoveMojo removeMojo) {
		assertDoesNotThrow(() -> removeMojo.setSkipRm(false).setSkip(true).execute());
		assertDoesNotThrow(() -> removeMojo.setSkipRm(true).setSkip(false).execute());
		assertDoesNotThrow(() -> removeMojo.setSkipRm(true).setSkip(true).execute());
	}

	@DisplayName("without container")
	@Test
	void withoutContainer(RemoveMojo removeMojo) {
		assertDoesNotThrow(removeMojo::execute);
	}

	@DisplayName("without container but present cache")
	@Test
	void withoutContainerButPresentCache(RemoveMojo removeMojo) {
		docker.createVolume();
		removeMojo.setIncludeCache(true);
		assertDoesNotThrow(removeMojo::execute);
		assertFalse(docker.isVolumePresent());
	}

	@DisplayName("with container")
	@Test
	void withContainer(CreateMojo createMojo, RemoveMojo removeMojo) {
		createMojo.setAgentCache(AgentCacheMode.VOLUME);
		assertDoesNotThrow(createMojo::execute);
		assertDoesNotThrow(removeMojo::execute);
		assertFalse(docker.getContainer().isPresent());
		assertTrue(docker.isVolumePresent());
	}

	@DisplayName("with container and cache")
	@Test
	void withContainerAndCache(CreateMojo createMojo, RemoveMojo removeMojo) {
		createMojo.setAgentCache(AgentCacheMode.VOLUME);
		removeMojo.setIncludeCache(true);
		assertDoesNotThrow(createMojo::execute);
		assertDoesNotThrow(removeMojo::execute);
		assertFalse(docker.getContainer().isPresent());
		assertFalse(docker.isVolumePresent());
	}
}