package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.AbstractTest;
import io.kokuwa.maven.k3s.AgentCacheMode;

@DisplayName("lifecycle")
public class LifecycleTest extends AbstractTest {

	@Test
	void withAgentCacheNone(
			CreateMojo create,
			StartMojo start,
			ImageMojo image,
			KubectlMojo kubectl,
			RemoveMojo remove) {
		lifecycle(create.setAgentCache(AgentCacheMode.NONE), start, image, kubectl, remove);
	}

	@Test
	void withAgentCacheVolume(
			CreateMojo create,
			StartMojo start,
			ImageMojo image,
			KubectlMojo kubectl,
			RemoveMojo remove) {
		lifecycle(create.setAgentCache(AgentCacheMode.VOLUME), start, image, kubectl, remove);
		assertTrue(docker.isVolumePresent());
		docker.removeVolume();
	}

	@Test
	void withAgentCacheHost(
			CreateMojo create,
			StartMojo start,
			ImageMojo image,
			KubectlMojo kubectl,
			RemoveMojo remove) {
		lifecycle(create.setAgentCache(AgentCacheMode.HOST), start, image, kubectl, remove);
	}

	private void lifecycle(
			CreateMojo create,
			StartMojo start,
			ImageMojo image,
			KubectlMojo kubectl,
			RemoveMojo remove) {
		assertDoesNotThrow(() -> create.setPortBindings(Arrays.asList("8080:8080")).execute());
		assertDoesNotThrow(() -> start.execute());
		assertDoesNotThrow(() -> image.setCtrImages(List.of("jmalloc/echo-server:0.3.1")).execute());
		assertDoesNotThrow(() -> kubectl.setCommand("kubectl apply -f pod.yaml").execute());
		assertPodRunning();
		assertDoesNotThrow(() -> remove.execute());
	}
}
