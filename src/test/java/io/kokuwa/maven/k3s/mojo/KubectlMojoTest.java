package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.test.AbstractTest;

/**
 * Test for {@link KubectlMojo}.
 *
 * @author stephan.schnabel@posteo.de
 */
@DisplayName("mojo: kubectl")
public class KubectlMojoTest extends AbstractTest {

	@DisplayName("with skip")
	@Test
	void withSkip(KubectlMojo kubectlMojo) {
		assertFalse(docker.getContainer().isPresent());
		assertDoesNotThrow(() -> kubectlMojo.setSkipKubectl(false).setSkip(true).execute());
		assertDoesNotThrow(() -> kubectlMojo.setSkipKubectl(true).setSkip(false).execute());
		assertDoesNotThrow(() -> kubectlMojo.setSkipKubectl(true).setSkip(true).execute());
	}

	@DisplayName("without container")
	@Test
	void withoutContainer(KubectlMojo kubectlMojo) {
		assertThrowsExactly(MojoExecutionException.class, kubectlMojo::execute, () -> "No k3s container found");
	}
}
