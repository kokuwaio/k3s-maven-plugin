package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.test.AbstractTest;

/**
 * Test for {@link ApplyMojo}.
 *
 * @author stephan.schnabel@posteo.de
 */
@DisplayName("mojo: apply")
public class ApplyMojoTest extends AbstractTest {

	@DisplayName("with skip")
	@Test
	void withSkip(ApplyMojo applyMojo) throws MojoExecutionException {

		assertFalse(docker.getContainer().isPresent());

		applyMojo.setSkipApply(false);
		applyMojo.setSkip(true);
		assertDoesNotThrow(applyMojo::execute);

		applyMojo.setSkipApply(true);
		applyMojo.setSkip(false);
		assertDoesNotThrow(applyMojo::execute);

		applyMojo.setSkipApply(true);
		applyMojo.setSkip(true);
		assertDoesNotThrow(applyMojo::execute);
	}

	@DisplayName("without container")
	@Test
	void withoutContainer(ApplyMojo kubectlMojo) {
		assertThrowsExactly(MojoExecutionException.class, kubectlMojo::execute, () -> "No k3s container found");
	}
}
