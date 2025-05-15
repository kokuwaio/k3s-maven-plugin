package io.kokuwa.maven.k3s.test;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kokuwa.maven.k3s.util.Docker;

/**
 * Base class for all test cases.
 *
 * @author stephan.schnabel@posteo.de
 */
@ExtendWith(MojoExtension.class)
@TestClassOrder(ClassOrderer.DisplayName.class)
@TestMethodOrder(MethodOrderer.DisplayName.class)
public abstract class AbstractTest {

	public Logger log = LoggerFactory.getLogger(getClass());
	public Docker docker;

	@BeforeEach
	@AfterEach
	void reset(Docker newDocker) throws MojoExecutionException, IOException {
		this.docker = newDocker;
		this.docker.removeContainer();
		this.docker.removeVolume();
		this.docker.removeImage(helloWorld());
		FileUtils.deleteDirectory(Paths.get("target/maven-status/k3s-maven-plugin").toFile());
		LoggerCapturer.clear();
	}

	public static String helloWorld() {
		return "hello-world:linux";
	}

	public List<String> exec(String... command) {
		try {
			return docker.exec(command);
		} catch (MojoExecutionException e) {
			fail("failed to exec command: " + List.of(command), e);
			return null;
		}
	}
}
