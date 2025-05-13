package io.kokuwa.maven.k3s;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.mojo.ApplyMojo;
import io.kokuwa.maven.k3s.mojo.ImageMojo;
import io.kokuwa.maven.k3s.mojo.RemoveMojo;
import io.kokuwa.maven.k3s.mojo.RunMojo;
import io.kokuwa.maven.k3s.test.AbstractTest;

/**
 * Test fof all mojos to simulate test lifecycle.
 *
 * @author stephan.schnabel@posteo.de
 */
@DisplayName("lifecycle")
public class LifecycleTest extends AbstractTest {

	@Test
	void lifecycle(RunMojo runMojo, ImageMojo imageMojo, ApplyMojo applyMojo, RemoveMojo removeMojo) {

		applyMojo.setManifests(new File("src/test/k3s/pod.yaml"));
		runMojo.setPortBindings(List.of("8080:8080"));
		imageMojo.setCtrImages(List.of(
				"jmalloc/echo-server:v0.3.7@sha256:c87f80de8dbb976a59b228fc9ecf257e0574c6f760c2f1c5f05f64c7ac7fbc1e"));

		assertDoesNotThrow(runMojo::execute);
		assertDoesNotThrow(imageMojo::execute);
		assertDoesNotThrow(applyMojo::execute);
		assertPodRunning();
		assertDoesNotThrow(removeMojo::execute);
	}
}
