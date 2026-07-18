package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.test.AbstractTest;
import io.kokuwa.maven.k3s.test.LoggerCapturer;
import io.kokuwa.maven.k3s.util.CtrImage;
import io.kokuwa.maven.k3s.util.Image;

/**
 * Test for {@link ImageMojo}.
 *
 * @author stephan@schnabel.org
 */
@DisplayName("mojo: image")
public class ImageMojoTest extends AbstractTest {

	@DisplayName("with skip")
	@Test
	void withSkip(ImageMojo imageMojo) throws MojoExecutionException {
		imageMojo.setDockerImages(List.of(helloWorld().toString()));

		imageMojo.setSkipImage(false);
		imageMojo.setSkip(true);
		assertDoesNotThrow(imageMojo::execute);

		imageMojo.setSkipImage(true);
		imageMojo.setSkip(false);
		assertDoesNotThrow(imageMojo::execute);

		imageMojo.setSkipImage(true);
		imageMojo.setSkip(true);
		assertDoesNotThrow(imageMojo::execute);

		assertFalse(hasDockerImage(helloWorld()));
	}

	@DisplayName("without container")
	@Test
	void withoutContainer(ImageMojo imageMojo) {
		imageMojo.setCtrImages(List.of(helloWorld().toString()));
		var exception = assertThrowsExactly(MojoExecutionException.class, imageMojo::execute, () -> "No container");
		assertEquals("No container found", exception.getMessage(), "Exception message invalid.");
	}

	@DisplayName("without images")
	@Test
	void withoutImages(ImageMojo imageMojo) {
		assertDoesNotThrow(imageMojo::execute);
	}

	@DisplayName("with crtImages")
	@Test
	void ctrImages(RunMojo runMojo, ImageMojo imageMojo) throws MojoExecutionException {
		assertDoesNotThrow(runMojo::execute);

		var image = helloWorld();
		var pull = "INFO io.kokuwa.maven.k3s.mojo.ImageMojo - Image " + image + " not found, start pulling";
		var skip = "DEBUG io.kokuwa.maven.k3s.mojo.ImageMojo - Image " + image + " found in ctr, skip pulling";
		var messages = LoggerCapturer.getMessages();
		imageMojo.setCtrImages(List.of(image.toString()));

		// pull image

		messages.clear();
		assertDoesNotThrow(imageMojo::execute);
		assertTrue(messages.contains(pull), "pull expected: " + messages);
		assertFalse(messages.contains(skip), "skip not expected: " + messages);

		// pull again

		messages.clear();
		assertDoesNotThrow(imageMojo::execute);
		assertFalse(messages.contains(pull), "pull not expected: " + messages);
		assertTrue(messages.contains(skip), "skip expected: " + messages);
	}

	@DisplayName("with dockerImages (without digest)")
	@Test
	void dockerImagesWithoutDigest(RunMojo runMojo, ImageMojo imageMojo) throws MojoExecutionException {
		assertDoesNotThrow(runMojo::execute);

		var image = Image.of("docker.io/library/hello-world:linux");
		var pull = "DEBUG io.kokuwa.maven.k3s.mojo.ImageMojo - Image " + image + " does not exists in ctr.";
		var skip = "INFO io.kokuwa.maven.k3s.mojo.ImageMojo - Image " + image + " present in ctr with digest "
				+ helloWorld().digest() + ", skip.";
		var change = "DEBUG io.kokuwa.maven.k3s.mojo.ImageMojo - Image " + image
				+ " present in ctr with digest nope, new digest is: " + helloWorld().digest();
		var messages = LoggerCapturer.getMessages();
		imageMojo.setDockerImages(List.of(image.toString()));

		// pull image because not present in host docker daemon

		messages.clear();
		assertFalse(hasDockerImage(image));
		assertCtrImage(image, false);
		assertDoesNotThrow(imageMojo::execute);
		assertTrue(hasDockerImage(image));
		assertCtrImage(image, true);
		assertTrue(messages.contains(pull), "pull expected: " + messages);
		assertFalse(messages.contains(skip), "skip not expected: " + messages);
		assertFalse(messages.contains(change), "change not expected: " + messages);

		// skip copy image because already present

		messages.clear();
		assertDoesNotThrow(imageMojo::execute);
		assertFalse(messages.contains(pull), "pull not expected: " + messages);
		assertTrue(messages.contains(skip), "skip expected: " + messages);
		assertFalse(messages.contains(change), "change not expected: " + messages);

		// pull again in docker, and copy to container because digest was changed

		messages.clear();
		imageMojo.setDockerPullAlways(true);
		exec("ctr", "image", "label", "docker.io/library/hello-world:linux", "k3s-maven-digest=nope");
		assertDoesNotThrow(imageMojo::execute);
		assertFalse(messages.contains(pull), "pull not expected: " + messages);
		assertFalse(messages.contains(skip), "skip not expected: " + messages);
		assertTrue(messages.contains(change), "change expected: " + messages);
	}

	@DisplayName("with dockerImages (with digest)")
	@Test
	void dockerImagesWithDigest(RunMojo runMojo, ImageMojo imageMojo) throws MojoExecutionException {
		assertDoesNotThrow(runMojo::execute);

		var image = helloWorld();
		var pull = "DEBUG io.kokuwa.maven.k3s.mojo.ImageMojo - Image " + image + " does not exists in ctr.";
		var skip = "INFO io.kokuwa.maven.k3s.mojo.ImageMojo - Image " + image + " present in ctr with digest "
				+ image.digest() + ", skip.";
		var change = "DEBUG io.kokuwa.maven.k3s.mojo.ImageMojo - Image " + image
				+ " present in ctr with digest nope, new digest is: " + image.digest();
		var messages = LoggerCapturer.getMessages();
		imageMojo.setDockerImages(List.of(image.toString()));

		// pull image because not present in host docker daemon

		messages.clear();
		assertFalse(hasDockerImage(image));
		assertCtrImage(image, false);
		assertDoesNotThrow(imageMojo::execute);
		assertTrue(hasDockerImage(image));
		assertCtrImage(image, true);
		assertTrue(messages.contains(pull), "pull expected: " + messages);
		assertFalse(messages.contains(skip), "skip not expected: " + messages);
		assertFalse(messages.contains(change), "change not expected: " + messages);

		// skip copy image because already present

		messages.clear();
		assertDoesNotThrow(imageMojo::execute);
		assertFalse(messages.contains(pull), "pull not expected: " + messages);
		assertTrue(messages.contains(skip), "skip expected: " + messages);
		assertFalse(messages.contains(change), "change not expected: " + messages);

		// pull again in docker, and copy to container because digest was changed

		messages.clear();
		imageMojo.setDockerPullAlways(true);
		exec("ctr", "image", "label", image.toString(), "k3s-maven-digest=nope");
		assertDoesNotThrow(imageMojo::execute);
		assertFalse(messages.contains(pull), "pull not expected: " + messages);
		assertFalse(messages.contains(skip), "skip not expected: " + messages);
		assertTrue(messages.contains(change), "change expected: " + messages);
	}

	@DisplayName("with tarFiles")
	@Test
	void tarFiles(RunMojo runMojo, ImageMojo imageMojo) throws MojoExecutionException, IOException {
		assertDoesNotThrow(runMojo::execute);

		var tarFile = Path.of("src/test/resources/hello-world.tar").toAbsolutePath();
		var copy = "DEBUG io.kokuwa.maven.k3s.mojo.ImageMojo - Tar " + tarFile + " does not exists in ctr.";
		var skip = "INFO io.kokuwa.maven.k3s.mojo.ImageMojo - Tar " + tarFile
				+ " present in ctr with checksum adler32:648119894, skip.";
		var change = "DEBUG io.kokuwa.maven.k3s.mojo.ImageMojo - Tar " + tarFile
				+ " present in ctr with checksum nope, new is: adler32:648119894";
		var success = "INFO io.kokuwa.maven.k3s.mojo.ImageMojo - Imported tar from " + tarFile
				+ " as docker.io/library/hello-world:linux";
		var messages = LoggerCapturer.getMessages();
		imageMojo.setTarFiles(List.of(tarFile.toString()));

		// import image

		messages.clear();
		assertDoesNotThrow(imageMojo::execute);
		assertTrue(messages.contains(copy), "copy expected: " + messages);
		assertFalse(messages.contains(skip), "skip not expected: " + messages);
		assertFalse(messages.contains(change), "change not expected: " + messages);
		assertTrue(messages.contains(success), "success expected: " + messages);

		// skip import because file did not change

		messages.clear();
		assertDoesNotThrow(imageMojo::execute);
		assertFalse(messages.contains(copy), "copy not expected: " + messages);
		assertTrue(messages.contains(skip), "skip expected: " + messages);
		assertFalse(messages.contains(change), "change not expected: " + messages);
		assertFalse(messages.contains(success), "success not  expected: " + messages);

		// re-import because file changed

		messages.clear();
		exec("ctr", "image", "label", "docker.io/library/hello-world:linux", "k3s-maven-tar-checksum=nope");
		assertDoesNotThrow(imageMojo::execute);
		assertFalse(messages.contains(copy), "copy not expected: " + messages);
		assertFalse(messages.contains(skip), "skip not expected: " + messages);
		assertTrue(messages.contains(change), "change expected: " + messages);
		assertTrue(messages.contains(success), "success expected: " + messages);
	}

	// internal

	private void assertCtrImage(Image image, boolean exists) throws MojoExecutionException {
		var images = docker.getCtrImages(docker.getContainer().get());
		assertEquals(exists, CtrImage.findByName(images, image).isPresent(),
				() -> "Image '" + image + "' " + (exists ? "not " : "") + "found, available: " + images);
	}

	private boolean hasDockerImage(Image image) throws MojoExecutionException {
		return docker.findImage(image).isPresent();
	}
}
