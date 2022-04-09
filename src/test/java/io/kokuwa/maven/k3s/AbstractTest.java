package io.kokuwa.maven.k3s;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.dockerjava.api.command.InspectVolumeResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Network;

import io.kokuwa.maven.k3s.mojo.RemoveMojo;
import io.kokuwa.maven.k3s.util.Docker;
import io.kokuwa.maven.test.MojoExtension;

@ExtendWith(MojoExtension.class)
public abstract class AbstractTest {

	protected final Docker docker = new Docker();
	private List<InspectVolumeResponse> volumes;
	private List<Container> containers;
	private List<Network> networks;

	// volumes

	@BeforeEach
	void setUp() {
		volumes = docker.listVolumes();
		containers = docker.listContainers();
		networks = docker.listNetworks();
	}

	@AfterEach
	void tearDown(RemoveMojo mojo) throws MojoExecutionException {

		// delete everything left in docker

		mojo.execute();

		// check for stuff left in docker

		var volumesAfter = docker.listVolumes();
		volumesAfter.removeIf(after -> volumes.stream().anyMatch(v -> v.getName().equals(after.getName())));
		assertTrue(volumesAfter.isEmpty(), "test leaked volume");

		var containersAfter = docker.listContainers();
		containersAfter.removeIf(after -> containers.stream().anyMatch(c -> c.getId().equals(after.getId())));
		assertTrue(containersAfter.isEmpty(), "test leaked container");

		var networksAfter = docker.listNetworks();
		networksAfter.removeIf(after -> networks.stream().anyMatch(v -> v.getId().equals(after.getId())));
		assertTrue(networksAfter.isEmpty(), "test leaked networks");
	}

	// assertions

	public static void assertPodRunning() {
		var response = assertDoesNotThrow(() -> HttpClient.newHttpClient().send(HttpRequest.newBuilder()
				.GET()
				.uri(URI.create("http://localhost:8080"))
				.version(Version.HTTP_1_1)
				.build(), HttpResponse.BodyHandlers.ofString()));
		assertEquals(200, response.statusCode(), "status");
		assertTrue(response.body().startsWith("Request served by echo"), "body");
	}
}
