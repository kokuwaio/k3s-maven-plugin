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

import io.kokuwa.maven.k3s.mojo.PullMojo;
import io.kokuwa.maven.k3s.mojo.RemoveMojo;
import io.kokuwa.maven.test.MojoExtension;

@ExtendWith(MojoExtension.class)
public abstract class AbstractTest {

	private List<InspectVolumeResponse> volumes;
	private List<Container> containers;
	private List<Network> networks;

	// volumes

	@BeforeEach
	void setUp() throws MojoExecutionException {
		volumes = volumes();
		containers = containers();
		networks = networks();
	}

	@AfterEach
	void tearDown() throws MojoExecutionException {

		// delete everything left in docker

		new RemoveMojo().execute();

		// check for stuff left in docker

		var volumesAfter = volumes();
		volumesAfter.removeIf(after -> volumes.stream().anyMatch(v -> v.getName().equals(after.getName())));
		assertTrue(volumesAfter.isEmpty(), "test leaked volume");

		var containersAfter = containers();
		containersAfter.removeIf(after -> containers.stream().anyMatch(c -> c.getId().equals(after.getId())));
		assertTrue(containersAfter.isEmpty(), "test leaked container");

		var networksAfter = networks();
		networksAfter.removeIf(after -> networks.stream().anyMatch(v -> v.getId().equals(after.getId())));
		assertTrue(networksAfter.isEmpty(), "test leaked networks");
	}

	List<InspectVolumeResponse> volumes() throws MojoExecutionException {
		return new PullMojo().dockerClient().listVolumesCmd().exec().getVolumes();
	}

	List<Network> networks() throws MojoExecutionException {
		return new PullMojo().dockerClient().listNetworksCmd().exec();
	}

	List<Container> containers() throws MojoExecutionException {
		return new PullMojo().dockerClient().listContainersCmd().exec();
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
