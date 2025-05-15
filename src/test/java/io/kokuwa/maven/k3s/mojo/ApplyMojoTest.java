package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.test.AbstractTest;
import io.kokuwa.maven.k3s.test.LoggerCapturer;

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
	void withoutContainer(ApplyMojo applyMojo) {
		assertThrowsExactly(MojoExecutionException.class, applyMojo::execute, () -> "No k3s container found");
	}

	@DisplayName("with crd from subdir")
	@Test
	void withCrd(RunMojo runMojo, ApplyMojo applyMojo) {
		applyMojo.setSubdir("crd");
		assertDoesNotThrow(runMojo::execute);
		assertDoesNotThrow(applyMojo::execute);
		assertFalse(LoggerCapturer.getMessages().contains("WARN This may cause issues!"), "No taint expected.");
	}

	@DisplayName("taint: node.kubernetes.io/disk-pressure")
	@Test
	void taintDiskPressure(RunMojo runMojo, ApplyMojo applyMojo) throws MojoExecutionException {
		assertDoesNotThrow(runMojo::execute);

		// write file to get disk usage above 95%
		var sizes = exec("df", "--output=size,used", "--block-size=1MiB", "/").get(1).strip().split(" ");
		var totalSize = Long.parseLong(sizes[0]);
		var usedSize = Long.parseLong(sizes[1]);
		exec("fallocate", "-l", ((long) (totalSize * 0.95)) - usedSize + "MiB", "/spam");
		exec("df", "--block-size=1MiB", "/");
		exec("kubectl", "wait", "--for=condition=DiskPressure", "node", "k3s");

		var e = assertThrowsExactly(MojoExecutionException.class, applyMojo::execute, () -> "no exception");
		assertEquals("Node has taints [node.kubernetes.io/disk-pressure] with effect NoSchedule", e.getMessage());
		assertTrue(LoggerCapturer.getMessages().contains("ERROR io.kokuwa.maven.k3s.mojo.ApplyMojo - "
				+ "Found node taints with effect NoSchedule: [node.kubernetes.io/disk-pressure]"),
				"Log message not found: \n" + LoggerCapturer.getMessages().stream().collect(Collectors.joining("\n")));
	}

	@DisplayName("taint: bar")
	@Test
	void taintBar(RunMojo runMojo, ApplyMojo applyMojo) throws MojoExecutionException {
		assertDoesNotThrow(runMojo::execute);
		exec("kubectl", "taint", "nodes", "k3s", "bar:NoSchedule");
		var e = assertThrowsExactly(MojoExecutionException.class, applyMojo::execute, () -> "no exception");
		assertEquals("Node has taints [bar] with effect NoSchedule", e.getMessage());
		assertTrue(LoggerCapturer.getMessages().contains("ERROR io.kokuwa.maven.k3s.mojo.ApplyMojo - "
				+ "Found node taints with effect NoSchedule: [bar]"),
				"Log message not found: \n" + LoggerCapturer.getMessages().stream().collect(Collectors.joining("\n")));
	}
}
