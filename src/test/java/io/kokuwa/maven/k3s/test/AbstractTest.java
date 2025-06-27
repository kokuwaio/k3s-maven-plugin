package io.kokuwa.maven.k3s.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kokuwa.maven.k3s.mojo.RunMojo;
import io.kokuwa.maven.k3s.util.Docker;

/**
 * Base class for all test cases.
 *
 * @author stephan@schnabel.org
 */
@ExtendWith(MojoExtension.class)
@TestClassOrder(ClassOrderer.DisplayName.class)
@TestMethodOrder(MethodOrderer.DisplayName.class)
public abstract class AbstractTest {

	public final Logger log = LoggerFactory.getLogger(getClass());
	public String host;
	public Docker docker;

	@BeforeEach
	void testBefore(TestInfo info, Docker newDocker, RunMojo runMojo) {
		reset(newDocker, runMojo);
		log.info("Before test {}", info.getDisplayName());
		LoggerCapturer.clear();
	}

	@AfterEach
	void testAfter(TestInfo info, Docker newDocker, RunMojo runMojo) {
		log.info("After test {}", info.getDisplayName());
		reset(newDocker, runMojo);
	}

	void reset(Docker newDocker, RunMojo runMojo) {
		this.host = System.getenv().getOrDefault("DOCKER_HOST_IP", "127.0.0.1");
		this.docker = newDocker;
		this.docker.getContainer().ifPresent(docker::remove);
		this.docker.removeVolume();
		this.docker.removeImage(helloWorld());
		assertDoesNotThrow(() -> runMojo.getMarker().consumeStarted());
		LoggerCapturer.clear();
	}

	public static String helloWorld() {
		return "hello-world:linux";
	}

	public List<String> exec(String... command) {
		try {
			return docker.exec(docker.getContainer().get(), command);
		} catch (MojoExecutionException e) {
			fail("failed to exec command: " + List.of(command), e);
			return null;
		}
	}
}
