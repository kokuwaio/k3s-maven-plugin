package io.kokuwa.maven.k3s.mojo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.test.AbstractTest;

/**
 * Test for {@link HostnameMojo}.
 *
 * @author stephan@schnabel.org
 */
@DisplayName("mojo: hostname")
public class HostnameMojoTest extends AbstractTest {

	@DisplayName("with skip")
	@Test
	void withSkip(HostnameMojo hostnameMojo, MavenProject project) {
		hostnameMojo.setSkipHostname(false);
		hostnameMojo.setSkip(true);
		assertDoesNotThrow(hostnameMojo::execute);
		assertTrue(project.getProperties().isEmpty());

		hostnameMojo.setSkipHostname(true);
		hostnameMojo.setSkip(false);
		assertDoesNotThrow(hostnameMojo::execute);
		assertTrue(project.getProperties().isEmpty());

		hostnameMojo.setSkipHostname(true);
		hostnameMojo.setSkip(true);
		assertDoesNotThrow(hostnameMojo::execute);
		assertTrue(project.getProperties().isEmpty());
	}

	@DisplayName("with DOCKER_HOST=tcp://1.2.3.4:2375")
	@Test
	void withDockerHostIp(HostnameMojo hostnameMojo, MavenProject project) {
		hostnameMojo.setHostnameCommand(null);
		hostnameMojo.setEnv(Map.of("DOCKER_HOST", "tcp://1.2.3.4:2375"));
		assertDoesNotThrow(hostnameMojo::execute);
		assertEquals("1.2.3.4", project.getProperties().get("k3s.hostname"));
	}

	@DisplayName("with DOCKER_HOST=tcp://my-docker-daemon:2375")
	@Test
	void withDockerHostLocalhost(HostnameMojo hostnameMojo, MavenProject project) {
		hostnameMojo.setHostnameCommand(null);
		hostnameMojo.setEnv(Map.of("DOCKER_HOST", "tcp://my-docker-daemon:2375"));
		assertDoesNotThrow(hostnameMojo::execute);
		assertEquals("my-docker-daemon", project.getProperties().get("k3s.hostname"));
	}

	@DisplayName("with DOCKER_HOST=unix:///var/run/docker.sock")
	@Test
	void withDockerHostSocket(HostnameMojo hostnameMojo) {
		hostnameMojo.setHostnameCommand(null);
		hostnameMojo.setEnv(Map.of("DOCKER_HOST", "unix:///var/run/docker.sock"));
		var exception = assertThrowsExactly(MojoExecutionException.class, hostnameMojo::execute);
		assertEquals("Failed to determine hostname", exception.getMessage(), "Exception message invalid.");
	}

	@DisplayName("with DOCKER_HOST=garbage")
	@Test
	void withDockerHostGarbage(HostnameMojo hostnameMojo) {
		hostnameMojo.setHostnameCommand(null);
		hostnameMojo.setEnv(Map.of("DOCKER_HOST", "garbage"));
		var exception = assertThrowsExactly(MojoExecutionException.class, hostnameMojo::execute);
		assertEquals("Failed to determine hostname", exception.getMessage(), "Exception message invalid.");
	}

	@DisplayName("with docker approach")
	@Test
	void withDocker(HostnameMojo hostnameMojo, MavenProject project) {
		hostnameMojo.setEnv(Map.of());
		assertDoesNotThrow(hostnameMojo::execute);
		assertNotNull(project.getProperties().get("k3s.hostname"));
	}

	@DisplayName("with custom property")
	@Test
	void witProperty(HostnameMojo hostnameMojo, MavenProject project) {
		hostnameMojo.setHostnameCommand(null);
		hostnameMojo.setHostnameProperty("k3s.ip");
		hostnameMojo.setEnv(Map.of("DOCKER_HOST", "tcp://1.2.3.4:2375"));
		assertDoesNotThrow(hostnameMojo::execute);
		assertNull(project.getProperties().get("k3s.hostname"));
		assertEquals("1.2.3.4", project.getProperties().get("k3s.ip"));
	}
}
