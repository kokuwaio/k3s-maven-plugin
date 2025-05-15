package io.kokuwa.maven.k3s.test;

import java.io.IOException;
import java.nio.file.Paths;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

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
}
