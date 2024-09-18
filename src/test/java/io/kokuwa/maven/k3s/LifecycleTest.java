package io.kokuwa.maven.k3s;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.File;
import java.util.List;

import io.kokuwa.maven.k3s.mojo.ApplyMojo;
import io.kokuwa.maven.k3s.mojo.ImageMojo;
import io.kokuwa.maven.k3s.mojo.RemoveMojo;
import io.kokuwa.maven.k3s.mojo.RunMojo;
import io.kokuwa.maven.k3s.test.AbstractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
		imageMojo.setCtrImages(List.of("jmalloc/echo-server:0.3.1"));

		assertDoesNotThrow(runMojo::execute);
		assertDoesNotThrow(imageMojo::execute);
		assertDoesNotThrow(applyMojo::execute);
		assertPodRunning();
		assertDoesNotThrow(removeMojo::execute);
	}
}
