package io.kokuwa.maven.k3s.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.BiConsumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link Image}.
 *
 * @author stephan@schnabel.org
 */
@DisplayName("util: image")
public class ImageTest {

	@DisplayName("of()")
	@Test
	void of() {
		BiConsumer<String, String> assertImage = (e, i) -> assertEquals(e, Image.of(i).toString(), i + " produced:"
				+ "\n registry:   " + Image.of(i).registry()
				+ "\n repository: " + Image.of(i).repository()
				+ "\n tag:        " + Image.of(i).tag()
				+ "\n digest:     " + Image.of(i).digest());

		assertImage.accept("docker.io/library/hello", "hello");
		assertImage.accept("docker.io/library/hello:latest", "hello:latest");
		assertImage.accept("docker.io/library/hello:0.1.23", "hello:0.1.23");
		assertImage.accept("docker.io/library/hello@sha256:XYZ", "hello@sha256:XYZ");
		assertImage.accept("docker.io/library/hello:0.1.23@sha256:XYZ", "hello:0.1.23@sha256:XYZ");

		assertImage.accept("docker.io/library/world", "docker.io/world");
		assertImage.accept("docker.io/library/world:latest", "docker.io/world:latest");
		assertImage.accept("docker.io/library/world:0.1.23", "docker.io/world:0.1.23");
		assertImage.accept("docker.io/library/world@sha256:XYZ", "docker.io/world@sha256:XYZ");
		assertImage.accept("docker.io/library/world:0.1.23@sha256:XYZ", "docker.io/world:0.1.23@sha256:XYZ");

		assertImage.accept("docker.io/hello/world", "hello/world");
		assertImage.accept("docker.io/hello/world:latest", "hello/world:latest");
		assertImage.accept("docker.io/hello/world:0.1.23", "hello/world:0.1.23");
		assertImage.accept("docker.io/hello/world@sha256:XYZ", "hello/world@sha256:XYZ");
		assertImage.accept("docker.io/hello/world:0.1.23@sha256:XYZ", "hello/world:0.1.23@sha256:XYZ");

		assertImage.accept("docker.io/hello/world", "docker.io/hello/world");
		assertImage.accept("docker.io/hello/world:latest", "docker.io/hello/world:latest");
		assertImage.accept("docker.io/hello/world:0.1.23", "docker.io/hello/world:0.1.23");
		assertImage.accept("docker.io/hello/world@sha256:XYZ", "docker.io/hello/world@sha256:XYZ");
		assertImage.accept("docker.io/hello/world:0.1.23@sha256:XYZ", "docker.io/hello/world:0.1.23@sha256:XYZ");

		assertImage.accept("quay.io/hello/world", "quay.io/hello/world");
		assertImage.accept("quay.io/hello/world:latest", "quay.io/hello/world:latest");
		assertImage.accept("quay.io/hello/world:0.1.23", "quay.io/hello/world:0.1.23");
		assertImage.accept("quay.io/hello/world@sha256:XYZ", "quay.io/hello/world@sha256:XYZ");
		assertImage.accept("quay.io/hello/world:0.1.23@sha256:XYZ", "quay.io/hello/world:0.1.23@sha256:XYZ");
	}
}
