package io.kokuwa.maven.k3s.mojo;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.AbstractTest;

@DisplayName("lifecycle")
public class LifecycleTest extends AbstractTest {

	@Test
	void lifecycle(
			CreateMojo create,
			StartMojo start,
			ImageMojo image,
			KubectlMojo kubectl,
			RemoveMojo remove) throws Exception {
		create.setPortBindings(Arrays.asList("8080:8080")).execute();
		start.execute();
		image.setCtrImages(List.of("jmalloc/echo-server:0.3.1")).execute();
		kubectl.setCommand("kubectl apply -f pod.yaml").execute();
		assertPodRunning();
		remove.execute();
	}
}
