package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.test.AbstractTest;

/**
 * Test for {@link RemoveMojo}.
 *
 * @author stephan@schnabel.org
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
		assertFalse(Files.exists(kubeconfig), "kubeconfig found");
	}

	@DisplayName("without container but present cache")
	@Test
	void withoutContainerButPresentCache(RemoveMojo removeMojo) throws MojoExecutionException {
		docker.createVolume();
		removeMojo.setIncludeCache(true);
		assertDoesNotThrow(removeMojo::execute);
		assertFalse(docker.isVolumePresent());
		assertFalse(Files.exists(kubeconfig), "kubeconfig found");
	}

	@DisplayName("with container")
	@Test
	void withContainer(RunMojo runMojo, RemoveMojo removeMojo) throws MojoExecutionException {
		assertDoesNotThrow(runMojo::execute);
		assertTrue(Files.exists(kubeconfig), "kubeconfig not found");
		assertDoesNotThrow(removeMojo::execute);
		assertFalse(docker.getContainer().isPresent());
		assertTrue(docker.isVolumePresent());
		assertFalse(Files.exists(kubeconfig), "kubeconfig found");
	}

	@DisplayName("with container and cache")
	@Test
	void withContainerAndCache(RunMojo runMojo, RemoveMojo removeMojo) throws MojoExecutionException {
		var k8s = Paths.get("target/k8s.yaml");
		removeMojo.setIncludeCache(true);
		removeMojo.setKubeconfig(k8s.toFile());
		runMojo.setKubeconfig(k8s.toFile());
		assertDoesNotThrow(runMojo::execute);
		assertTrue(Files.exists(k8s), "kubeconfig not found");
		assertDoesNotThrow(removeMojo::execute);
		assertFalse(docker.getContainer().isPresent());
		assertFalse(docker.isVolumePresent());
		assertFalse(Files.exists(k8s), "kubeconfig found");
	}
}
