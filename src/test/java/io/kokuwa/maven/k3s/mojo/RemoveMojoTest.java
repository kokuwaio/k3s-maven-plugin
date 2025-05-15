package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.maven.plugin.MojoExecutionException;
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

		removeMojo.setSkipRm(false);
		removeMojo.setSkip(true);
		assertDoesNotThrow(removeMojo::execute);

		removeMojo.setSkipRm(true);
		removeMojo.setSkip(false);
		assertDoesNotThrow(removeMojo::execute);

		removeMojo.setSkipRm(true);
		removeMojo.setSkip(true);
		assertDoesNotThrow(removeMojo::execute);
	}

	@DisplayName("without container")
	@Test
	void withoutContainer(RemoveMojo removeMojo) {
		assertDoesNotThrow(removeMojo::execute);
	}

	@DisplayName("without container but present cache")
	@Test
	void withoutContainerButPresentCache(RemoveMojo removeMojo) throws MojoExecutionException {
		docker.createVolume();
		removeMojo.setIncludeCache(true);
		assertDoesNotThrow(removeMojo::execute);
		assertFalse(docker.isVolumePresent());
	}

	@DisplayName("with container")
	@Test
	void withContainer(RunMojo runMojo, RemoveMojo removeMojo) throws MojoExecutionException {
		assertDoesNotThrow(runMojo::execute);
		assertDoesNotThrow(removeMojo::execute);
		assertFalse(docker.getContainer().isPresent());
		assertTrue(docker.isVolumePresent());
	}

	@DisplayName("with container and cache")
	@Test
	void withContainerAndCache(RunMojo runMojo, RemoveMojo removeMojo) throws MojoExecutionException {
		removeMojo.setIncludeCache(true);
		assertDoesNotThrow(runMojo::execute);
		assertDoesNotThrow(removeMojo::execute);
		assertFalse(docker.getContainer().isPresent());
		assertFalse(docker.isVolumePresent());
	}
}
