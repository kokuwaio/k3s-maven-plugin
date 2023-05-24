package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
	@Disabled("flaky because of host mount")
	@Test
	void withoutContainerButPresentCache(RemoveMojo removeMojo) {
		docker.createVolume();
		removeMojo.setIncludeCache(true);
		assertDoesNotThrow(removeMojo::execute);
		assertFalse(docker.isVolumePresent());
	}

	@DisplayName("with container")
	@Test
	void withContainer(RunMojo runMojo, RemoveMojo removeMojo) {
		assertDoesNotThrow(runMojo::execute);
		assertDoesNotThrow(removeMojo::execute);
		assertFalse(docker.getContainer().isPresent());
		assertTrue(docker.isVolumePresent());
	}

	@DisplayName("with container and cache")
	@Disabled("flaky because of host mount")
	@Test
	void withContainerAndCache(RunMojo runMojo, RemoveMojo removeMojo) {
		removeMojo.setIncludeCache(true);
		assertDoesNotThrow(runMojo::execute);
		assertDoesNotThrow(removeMojo::execute);
		assertFalse(docker.getContainer().isPresent());
		assertFalse(docker.isVolumePresent());
	}
}
