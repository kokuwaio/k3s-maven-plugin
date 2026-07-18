package io.kokuwa.maven.k3s.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.apache.commons.lang3.function.TriConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link CtrImage}.
 *
 * @author stephan@schnabel.org
 */
@DisplayName("util: ctr image")
public class CtrImageTest {

	@DisplayName("name()")
	@Test
	void name() {
		TriConsumer<String, String, String> assertImage = (e, ref, digest) -> assertEquals(e,
				new CtrImage(ref, digest, null, null).name().toString(), ref + " " + digest + " produced:"
						+ "\n ref:    " + ref
						+ "\n digest: " + digest);
		assertImage.accept("docker.io/hello/world@sha256:XYZ", "docker.io/hello/world", "sha256:XYZ");
		assertImage.accept("docker.io/hello/world@sha256:XYZ", "docker.io/hello/world@sha256:XYZ", "sha256:XYZ");
		assertImage.accept("docker.io/hello/world:t@sha256:XYZ", "docker.io/hello/world:t", "sha256:XYZ");
		assertImage.accept("docker.io/hello/world:t@sha256:XYZ", "docker.io/hello/world:t@sha256:XYZ", "sha256:XYZ");
	}

	@DisplayName("findByName()")
	@Test
	void findByName() {
		var image1 = new CtrImage("docker.io/hi/world@sha256:XYZ", "sha256:XYZ", "1", null);
		var image2 = new CtrImage("docker.io/hi/earth", "sha256:ABC", "1", null);
		var image3 = new CtrImage("docker.io/hi/dude:1", "sha256:DEF", "1", null);
		var images = List.of(image1, image2, image3);

		assertEquals(image1, CtrImage.findByName(images, Image.of("docker.io/hi/world@sha256:XYZ")).orElse(null));
		assertEquals(image2, CtrImage.findByName(images, Image.of("docker.io/hi/earth")).orElse(null));
		assertEquals(image3, CtrImage.findByName(images, Image.of("docker.io/hi/dude:1")).orElse(null));
		assertEquals(null, CtrImage.findByName(images, Image.of("nope")).orElse(null));
	}

	@DisplayName("findByDigest()")
	@Test
	void findByDigest() {
		var images = List.of(new CtrImage("docker.io/hi/world@sha256:XYZ", "sha256:XYZ", "1", null));
		assertTrue(CtrImage.findByDigest(images, "sha256:XYZ").isPresent());
		assertTrue(CtrImage.findByDigest(images, "asd").isEmpty());
	}
}
