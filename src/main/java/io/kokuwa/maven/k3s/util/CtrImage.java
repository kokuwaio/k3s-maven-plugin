package io.kokuwa.maven.k3s.util;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Wrapper for ctr image ls.
 *
 * @author stephan@schnabel.org
 * @since 2.3.0
 */
public record CtrImage(String ref, String digest, String size, Map<String, String> labels) {

	Image name() {
		return Image.of(ref.endsWith(digest) ? ref : ref + "@" + digest);
	}

	public static Optional<CtrImage> findByName(List<CtrImage> images, Image name) {
		if (name.digest() != null) {
			return findByDigest(images, name.digest());
		}
		return images.stream()
				.filter(i -> Objects.equals(i.name().registry(), name.registry()))
				.filter(i -> Objects.equals(i.name().repository(), name.repository()))
				.filter(i -> Objects.equals(i.name().tag(), name.tag()))
				.findAny();
	}

	public static Optional<CtrImage> findByDigest(List<CtrImage> images, String digest) {
		return images.stream().filter(i -> i.digest().equals(digest)).findAny();
	}
}
