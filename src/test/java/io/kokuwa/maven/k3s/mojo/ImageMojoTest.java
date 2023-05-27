package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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
	void withSkip(ImageMojo imageMojo) throws MojoExecutionException {
		imageMojo.setDockerImages(List.of(helloWorld()));

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
	}

	@DisplayName("with dockerImages")
	@Test
	void dockerImages(RunMojo runMojo, ImageMojo imageMojo) throws MojoExecutionException {

		imageMojo.setDockerImages(List.of(helloWorld()));
		assertDoesNotThrow(runMojo::execute);

		// pull image because not present in host docker daemon

		assertFalse(hasDockerImage(helloWorld()));
		assertCtrImage(helloWorld(), false);
		assertDoesNotThrow(imageMojo::execute);
		assertTrue(hasDockerImage(helloWorld()));
		assertCtrImage(helloWorld(), true);

		// skip copy image because already present

		assertCtrImage(helloWorld(), true);
		assertDoesNotThrow(imageMojo::execute);
		assertCtrImage(helloWorld(), true);

		// pull again in docker, and copy to container because digest was changed

		imageMojo.setDockerPullAlways(true);
		docker.exec("ctr", "image", "label", docker.normalizeImage(helloWorld()), "k3s-maven-digest=nope");
		assertCtrImage(helloWorld(), true);
		assertDoesNotThrow(imageMojo::execute);
		assertCtrImage(helloWorld(), true);
	}

	// test

	private void assertCtrPull(ImageMojo mojo) throws MojoExecutionException {

		removeCtrImage(helloWorld());
		mojo.setCtrImages(List.of(helloWorld()));
		mojo.setTarFiles(List.of());
		mojo.setDockerImages(List.of());

		assertFalse(hasDockerImage(helloWorld()));
		assertCtrImage(helloWorld(), false);
		assertDoesNotThrow(mojo::execute);
		assertFalse(hasDockerImage(helloWorld()));
		assertCtrImage(helloWorld(), true);
	}

	private void assertTagFiles(ImageMojo mojo) throws MojoExecutionException {

		removeCtrImage(helloWorld());
		mojo.setCtrImages(List.of());
		mojo.setTarFiles(List.of("src/test/resources/hello-world.tar"));
		mojo.setDockerImages(List.of());

		assertFalse(hasDockerImage(helloWorld()));
		assertCtrImage(helloWorld(), false);
		assertDoesNotThrow(mojo::execute);
		assertFalse(hasDockerImage(helloWorld()));
		assertCtrImage(helloWorld(), true);
	}

	// internal

	private void assertCtrImage(String image, boolean exists) throws MojoExecutionException {
		var images = docker.exec("ctr", "image", "list", "--quiet");
		var normalizedImage = docker.normalizeImage(image);
		assertEquals(exists, images.contains(normalizedImage),
				"Image '" + normalizedImage + "' " + (exists ? "not " : "") + "found, available: \n" + images);
	}

	private boolean hasDockerImage(String image) throws MojoExecutionException {
		return docker.getImage(image).isPresent();
	}

	private void removeCtrImage(String image) throws MojoExecutionException {
		docker.exec("ctr", "image", "remove", docker.normalizeImage(image));
	}
}
