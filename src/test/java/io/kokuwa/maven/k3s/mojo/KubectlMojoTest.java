package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

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

	@DisplayName("exec in container")
	@Test
	void withExecInContainer(CreateMojo createMojo, StartMojo startMojo, KubectlMojo kubectlMojo) {
		createMojo.setPortBindings(List.of("8080:8080"));
		kubectlMojo.setKubectlPath(null).setCommand("kubectl apply -f pod.yaml");
		assertDoesNotThrow(createMojo::execute);
		assertDoesNotThrow(startMojo::execute);
		assertDoesNotThrow(kubectlMojo::execute);
		assertPodRunning();
	}

	@DisplayName("exec on host")
	@EnabledIf(value = "hasKubectl", disabledReason = "kubectl not found")
	@Test
	void withExecOnHost(CreateMojo createMojo, StartMojo startMojo, KubectlMojo kubectlMojo) {
		createMojo.setPortBindings(List.of("8080:8080"));
		kubectlMojo.setKubectlPath("/usr/local/bin/kubectl").setCommand("kubectl apply -f pod.yaml");
		assertDoesNotThrow(createMojo::execute);
		assertDoesNotThrow(startMojo::execute);
		assertDoesNotThrow(kubectlMojo::execute);
		assertPodRunning();
	}

	static boolean hasKubectl() {
		return Files.isReadable(Paths.get("/usr/local/bin/kubectl"));
	}
}
