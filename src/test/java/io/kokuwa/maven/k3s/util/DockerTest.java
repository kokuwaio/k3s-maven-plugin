package io.kokuwa.maven.k3s.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.function.BiConsumer;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.test.AbstractTest;

/**
 * Test for {@link Docker}.
 *
 * @author stephan.schnabel@posteo.de
 */
@DisplayName("util: docker")
public class DockerTest extends AbstractTest {

	@DisplayName("image handling")
	@Test
	void image() throws MojoExecutionException {

		assertFalse(docker.findImage(helloWorld()).isPresent(), "image found before testing");
		docker.removeImage(helloWorld());

		var callback = docker.pullImage(helloWorld());
		Await.await(log, "pull images").timeout(Duration.ofSeconds(300)).until(callback::isCompleted);
		if (!callback.isSuccess()) {
			throw new MojoExecutionException("Failed to pull image " + helloWorld());
		}
		assertTrue(docker.findImage(helloWorld()).isPresent(), "image missing after pulling");

		docker.removeImage(helloWorld());
		assertFalse(docker.findImage(helloWorld()).isPresent(), "image found after removing");
	}

	@DisplayName("normalizeImage()")
	@Test
	void normalizeImage() {

		BiConsumer<String, String> assertImage = (e, i) -> assertEquals(e, docker.normalizeDockerImage(i), i);

		assertImage.accept("docker.io/library/hello:latest", "hello");
		assertImage.accept("docker.io/library/hello:latest", "hello:latest");
		assertImage.accept("docker.io/library/hello:0.1.23", "hello:0.1.23");
		assertImage.accept("docker.io/library/hello@sha256:XYZ", "hello@sha256:XYZ");

		assertImage.accept("docker.io/hello/world:latest", "hello/world");
		assertImage.accept("docker.io/hello/world:latest", "hello/world:latest");
		assertImage.accept("docker.io/hello/world:0.1.23", "hello/world:0.1.23");
		assertImage.accept("docker.io/hello/world@sha256:XYZ", "hello/world@sha256:XYZ");

		assertImage.accept("docker.io/hello/world:latest", "docker.io/hello/world");
		assertImage.accept("docker.io/hello/world:latest", "docker.io/hello/world:latest");
		assertImage.accept("docker.io/hello/world:0.1.23", "docker.io/hello/world:0.1.23");
		assertImage.accept("docker.io/hello/world@sha256:XYZ", "docker.io/hello/world@sha256:XYZ");

		assertImage.accept("quay.io/hello/world:latest", "quay.io/hello/world");
		assertImage.accept("quay.io/hello/world:latest", "quay.io/hello/world:latest");
		assertImage.accept("quay.io/hello/world:0.1.23", "quay.io/hello/world:0.1.23");
		assertImage.accept("quay.io/hello/world@sha256:XYZ", "quay.io/hello/world@sha256:XYZ");
	}
}
