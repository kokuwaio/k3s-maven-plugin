package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.test.AbstractTest;

/**
 * Test for {@link ImageMojo}.
 *
 * @author stephan.schnabel@posteo.de
 */
@DisplayName("mojo: image")
public class ImageMojoTest extends AbstractTest {

	@DisplayName("with skip")
	@Test
	void withSkip(ImageMojo imageMojo) {
		imageMojo.setCtrImages(List.of(helloWorld()));
		assertDoesNotThrow(() -> imageMojo.setSkipImage(false).setSkip(true).execute());
		assertDoesNotThrow(() -> imageMojo.setSkipImage(true).setSkip(false).execute());
		assertDoesNotThrow(() -> imageMojo.setSkipImage(true).setSkip(true).execute());
	}

	@DisplayName("without container")
	@Test
	void withoutContainer(ImageMojo imageMojo) {
		imageMojo.setCtrImages(List.of(helloWorld()));
		assertThrowsExactly(MojoExecutionException.class, imageMojo::execute, () -> "No k3s container found");
	}

	@DisplayName("without images")
	@Test
	void withoutImages(ImageMojo imageMojo) {
		assertDoesNotThrow(imageMojo::execute);
	}

	@DisplayName("with images")
	@Test
	void withImages(RunMojo runMojo, ImageMojo imageMojo) throws MojoExecutionException {
		assertDoesNotThrow(runMojo::execute);
		assertCtrPull(imageMojo);
		assertTagFiles(imageMojo);
		assertDockerWithoutImage(imageMojo);
		assertDockerWithCachedImage(imageMojo);
	}

	// test

	private void assertCtrPull(ImageMojo mojo) throws MojoExecutionException {

		removeCtrImage(helloWorld());
		mojo.setCtrImages(List.of(helloWorld())).setTarFiles(List.of()).setDockerImages(List.of());

		assertFalse(hasDockerImage(helloWorld()));
		assertCtrImage(helloWorld(), false);
		assertDoesNotThrow(mojo::execute);
		assertFalse(hasDockerImage(helloWorld()));
		assertCtrImage(helloWorld(), true);
	}

	private void assertTagFiles(ImageMojo mojo) throws MojoExecutionException {

		removeCtrImage(helloWorld());
		mojo.setCtrImages(List.of()).setTarFiles(List.of("src/test/resources/hello-world.tar"))
				.setDockerImages(List.of());

		assertFalse(hasDockerImage(helloWorld()));
		assertCtrImage(helloWorld(), false);
		assertDoesNotThrow(mojo::execute);
		assertFalse(hasDockerImage(helloWorld()));
		assertCtrImage(helloWorld(), true);
	}

	private void assertDockerWithCachedImage(ImageMojo mojo) throws MojoExecutionException {

		removeCtrImage(helloWorld());
		mojo.setCtrImages(List.of()).setTarFiles(List.of()).setDockerImages(List.of(helloWorld()));

		assertTrue(hasDockerImage(helloWorld()));
		assertCtrImage(helloWorld(), false);
		assertDoesNotThrow(mojo::execute);
		assertTrue(hasDockerImage(helloWorld()));
		assertCtrImage(helloWorld(), true);

		mojo.setDockerPullAlways(true);
		assertDoesNotThrow(mojo::execute);
	}

	private void assertDockerWithoutImage(ImageMojo mojo) throws MojoExecutionException {

		docker.removeImage(helloWorld());
		removeCtrImage(helloWorld());
		mojo.setCtrImages(List.of()).setTarFiles(List.of()).setDockerImages(List.of(helloWorld()));

		assertFalse(hasDockerImage(helloWorld()));
		assertCtrImage(helloWorld(), false);
		assertDoesNotThrow(mojo::execute);
		assertTrue(hasDockerImage(helloWorld()));
		assertCtrImage(helloWorld(), true);
	}

	// internal

	private void assertCtrImage(String image, boolean exists) throws MojoExecutionException {
		var container = docker.getContainer().get();
		var result = docker.execThrows(container, "ctr image list --quiet", Duration.ofSeconds(30));
		var output = result.getMessages().stream().collect(Collectors.joining("\n"));
		var images = List.of(output.split("\n"));
		var normalizedImage = docker.normalizeDockerImage(image);
		assertEquals(exists, images.contains(normalizedImage),
				"Image '" + normalizedImage + "' " + (exists ? "not " : "") + "found, available: \n" + output);
	}

	private boolean hasDockerImage(String image) {
		return docker.findImage(image).isPresent();
	}

	private void removeCtrImage(String image) throws MojoExecutionException {
		docker.execThrows(
				docker.getContainer().get(),
				"ctr image remove " + docker.normalizeDockerImage(image),
				Duration.ofMinutes(1));
	}
}
