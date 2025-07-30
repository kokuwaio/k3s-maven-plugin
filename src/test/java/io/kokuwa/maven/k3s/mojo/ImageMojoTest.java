package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import io.kokuwa.maven.k3s.test.AbstractTest;

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
		var exception = assertThrowsExactly(MojoExecutionException.class, imageMojo::execute, () -> "No container");
		assertEquals("No container found", exception.getMessage(), "Exception message invalid.");
	}

	@DisplayName("without images")
	@Test
	void withoutImages(ImageMojo imageMojo) {
		assertDoesNotThrow(imageMojo::execute);
	}

	@DisabledIfEnvironmentVariable(named = "CI", matches = "woodpecker", disabledReason = "fails with k3s in k3s")
	@DisplayName("with crtImages")
	@Test
	void ctrImages(RunMojo runMojo, ImageMojo imageMojo) throws MojoExecutionException {

		imageMojo.setCtrImages(List.of(helloWorld()));
		assertDoesNotThrow(runMojo::execute);

		// pull image

		assertCtrImage(helloWorld(), false);
		assertDoesNotThrow(imageMojo::execute);
		assertCtrImage(helloWorld(), true);
	}

	@DisabledIfEnvironmentVariable(named = "CI", matches = "woodpecker", disabledReason = "fails with k3s in k3s")
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
		exec("ctr", "image", "label", docker.normalizeImage(helloWorld()), "k3s-maven-digest=nope");
		assertCtrImage(helloWorld(), true);
		assertDoesNotThrow(imageMojo::execute);
		assertCtrImage(helloWorld(), true);
	}

	@DisabledIfEnvironmentVariable(named = "CI", matches = "woodpecker")
	@DisplayName("with tarFiles")
	@Test
	void tarFiles(RunMojo runMojo, ImageMojo imageMojo) throws MojoExecutionException, IOException {

		var tarFile = Path.of("target/test-classes/tarFile.tar");
		imageMojo.setTarFiles(List.of(tarFile.toString()));
		assertDoesNotThrow(runMojo::execute);

		// import image

		assertCtrImage(helloWorld(), false);
		Files.copy(Path.of("src/test/resources/hello-world.tar"), tarFile, StandardCopyOption.REPLACE_EXISTING);
		assertDoesNotThrow(imageMojo::execute);
		assertCtrImage(helloWorld(), true);

		// skip import because file did not change

		assertDoesNotThrow(imageMojo::execute);

		// reimport because file changed

		Files.copy(Path.of("src/test/resources/hello-world.tar.old"), tarFile, StandardCopyOption.REPLACE_EXISTING);
		assertDoesNotThrow(imageMojo::execute);
	}

	// internal

	private void assertCtrImage(String image, boolean exists) throws MojoExecutionException {
		var images = exec("ctr", "image", "list", "--quiet");
		var normalizedImage = docker.normalizeImage(image);
		assertEquals(exists, images.stream().filter(i -> i.startsWith(normalizedImage)).findAny().isPresent(),
				"Image '" + normalizedImage + "' " + (exists ? "not " : "") + "found, available: \n" + images);
	}

	private boolean hasDockerImage(String image) throws MojoExecutionException {
		return docker.findImage(image).isPresent();
	}
}
