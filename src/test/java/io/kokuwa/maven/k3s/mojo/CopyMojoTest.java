package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.test.AbstractTest;

/**
 * Test for {@link CopyMojo}.
 *
 * @author stephan@schnabel.org
 */
@DisplayName("mojo: copy")
public class CopyMojoTest extends AbstractTest {

	@DisplayName("with skip")
	@Test
	void withSkip(CopyMojo copyMojo) {

		copyMojo.setSkipCopy(false);
		copyMojo.setSkip(true);
		assertDoesNotThrow(copyMojo::execute);

		copyMojo.setSkipCopy(true);
		copyMojo.setSkip(false);
		assertDoesNotThrow(copyMojo::execute);

		copyMojo.setSkipCopy(true);
		copyMojo.setSkip(true);
		assertDoesNotThrow(copyMojo::execute);
	}

	@DisplayName("without container")
	@Test
	void withoutContainer(CopyMojo copyMojo) {
		copyMojo.setCopySource(new File("target"));
		copyMojo.setCopyTarget(new File("/"));
		assertThrowsExactly(MojoExecutionException.class, copyMojo::execute, () -> "No k3s container found");
	}

	@DisplayName("source missing")
	@Test
	void failSourceMissing(CopyMojo copyMojo) {
		copyMojo.setCopySource(new File("nope"));
		copyMojo.setCopyTarget(new File("/"));
		var exception = assertThrowsExactly(MojoExecutionException.class, copyMojo::execute, () -> "File not found");
		assertEquals("Path nope not found.", exception.getMessage(), "Exception message invalid.");
	}

	@DisplayName("copy dir")
	@Test
	void copyDir(RunMojo runMojo, CopyMojo copyMojo) throws MojoExecutionException {
		copyMojo.setCopySource(new File("src"));
		copyMojo.setCopyTarget(new File("/source"));
		assertDoesNotThrow(runMojo::execute);
		assertDoesNotThrow(copyMojo::execute);
		exec("test", "-f", "/source/it/pom.xml");
	}

	@DisplayName("copy file")
	@Test
	void copyFile(RunMojo runMojo, CopyMojo copyMojo) throws MojoExecutionException {
		copyMojo.setCopySource(new File("pom.xml"));
		copyMojo.setCopyTarget(new File("/tmp"));
		assertDoesNotThrow(runMojo::execute);
		assertDoesNotThrow(copyMojo::execute);
		exec("test", "-f", "/tmp/pom.xml");
	}
}
