package io.kokuwa.maven.k3s;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.mojo.CreateMojo;
import io.kokuwa.maven.k3s.mojo.ImageMojo;
import io.kokuwa.maven.k3s.mojo.KubectlMojo;
import io.kokuwa.maven.k3s.mojo.RemoveMojo;
import io.kokuwa.maven.k3s.mojo.StartMojo;
import io.kokuwa.maven.k3s.test.AbstractTest;

/**
 * Test fof all mojos to simulate test lifecycle.
 *
 * @author stephan.schnabel@posteo.de
 */
@DisplayName("lifecycle")
public class LifecycleTest extends AbstractTest {

	@Test
	void withAgentCacheNone(
			CreateMojo createMojo,
			StartMojo startMojo,
			ImageMojo imageMojo,
			KubectlMojo kubectlMojo,
			RemoveMojo removeMojo) {
		lifecycle(createMojo.setAgentCache(AgentCacheMode.NONE), startMojo, imageMojo, kubectlMojo, removeMojo);
	}

	@Test
	void withAgentCacheVolume(
			CreateMojo createMojo,
			StartMojo startMojo,
			ImageMojo imageMojo,
			KubectlMojo kubectlMojo,
			RemoveMojo removeMojo) {
		lifecycle(createMojo.setAgentCache(AgentCacheMode.VOLUME), startMojo, imageMojo, kubectlMojo, removeMojo);
		assertTrue(docker.isVolumePresent());
		docker.removeVolume();
	}

	@Test
	void withAgentCacheHost(
			CreateMojo createMojo,
			StartMojo startMojo,
			ImageMojo imageMojo,
			KubectlMojo kubectlMojo,
			RemoveMojo removeMojo) {
		lifecycle(createMojo.setAgentCache(AgentCacheMode.HOST), startMojo, imageMojo, kubectlMojo, removeMojo);
	}

	private void lifecycle(
			CreateMojo createMojo,
			StartMojo startMojo,
			ImageMojo imageMojo,
			KubectlMojo kubectlMojo,
			RemoveMojo removeMojo) {
		assertDoesNotThrow(() -> createMojo.setPortBindings(List.of("8080:8080")).execute());
		assertDoesNotThrow(() -> startMojo.execute());
		assertDoesNotThrow(() -> imageMojo.setCtrImages(List.of("jmalloc/echo-server:0.3.1")).execute());
		assertDoesNotThrow(() -> kubectlMojo.setCommand("kubectl apply -f pod.yaml").execute());
		assertPodRunning();
		assertDoesNotThrow(() -> removeMojo.execute());
	}
}
