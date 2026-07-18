package io.kokuwa.maven.k3s.util;

/**
 * Wrapper for image name.
 *
 * @author stephan@schnabel.org
 * @since 2.3.0
 */
public final record Image(String registry, String repository, String tag, String digest) {

	@Override
	public final String toString() {
		return registry + "/" + repository + (tag == null ? "" : ":" + tag) + (digest == null ? "" : "@" + digest);
	}

	public static final Image of(String image) {
		var registry = "docker.io";
		var name = image.split("@")[0].split(":")[0];
		var tag = image.split("@")[0].contains(":") ? image.split("@")[0].split(":")[1] : null;
		var digest = image.contains("@") ? image.split("@")[1] : null;

		var slashIndex = name.indexOf('/');
		if (slashIndex == -1) {
			name = "library/" + name;
		} else if (name.substring(0, slashIndex).contains(".")) {
			registry = name.split("/")[0];
			name = name.substring(slashIndex + 1, name.length());
		}
		if (image.startsWith("docker.io/") && image.indexOf('/', 10) == -1) {
			name = "library/" + name;
		}

		return new Image(registry, name, tag, digest);
	}
}
