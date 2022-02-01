package io.kokuwa.maven.k3s;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.dockerjava.api.command.InspectVolumeResponse;

import io.kokuwa.maven.k3s.mojo.ApplyMojo;
import io.kokuwa.maven.k3s.mojo.KustomizeMojo;
import io.kokuwa.maven.k3s.mojo.PullMojo;
import io.kokuwa.maven.k3s.mojo.RemoveMojo;
import io.kokuwa.maven.k3s.mojo.StartMojo;
import io.kokuwa.maven.k3s.mojo.StopMojo;

@DisplayName("lifecycle")
public class LifecycleTest {

	List<InspectVolumeResponse> volumesBefore;
	PullMojo pull;
	StartMojo start;
	StopMojo stop;
	RemoveMojo remove;
	ApplyMojo apply;
	KustomizeMojo kustomize;

	@DisplayName("pull/start/apply/rm")
	@Test
	void kubectl() throws MojoExecutionException {
		pull.execute();
		start.setPortBindings(List.of("8080:8080")).execute();
		apply.setCommand("kubectl apply -f pod.yaml").execute();
		assertPodRunning();
		remove.execute();
	}

	@DisplayName("pull/start/kustomize/rm")
	@Test
	void kustomize() throws MojoExecutionException {
		pull.execute();
		start.setPortBindings(List.of("8080:8080")).execute();
		kustomize.execute();
		assertPodRunning();
		remove.execute();
	}

	@DisplayName("pull/start/apply/stop/stop/rm")
	@Test
	void stopped() throws MojoExecutionException {
		pull.execute();
		start.setPortBindings(List.of("8080:8080")).execute();
		apply.setManifests(new File("src/test/k3s/pod.yaml")).execute();
		assertPodRunning();
		stop.execute();
		stop.execute();
		remove.execute();
	}

	@DisplayName("pull/start/stop/start/rm")
	@Test
	void restart() throws MojoExecutionException {
		pull.execute();
		start.execute();
		stop.execute();
		start.execute();
		remove.execute();
	}

	@DisplayName("pull/rm")
	@Test
	void nothing() throws MojoExecutionException {
		pull.execute();
		remove.execute();
	}

	@DisplayName("pull/stop/rm")
	@Test
	void stopNothing() throws MojoExecutionException {
		pull.execute();
		stop.execute();
		remove.execute();
	}

	// internal

	@BeforeEach
	void setUp() throws MojoExecutionException {
		pull = mojo(new PullMojo());
		start = mojo(new StartMojo());
		stop = mojo(new StopMojo());
		remove = mojo(new RemoveMojo());
		apply = mojo(new ApplyMojo());
		kustomize = mojo(new KustomizeMojo());
		volumesBefore = pull.dockerClient().listVolumesCmd().exec().getVolumes();
	}

	@AfterEach
	void tearDown() throws MojoExecutionException {

		var volumesAfter = pull.dockerClient().listVolumesCmd().exec().getVolumes();
		volumesAfter.removeIf(after -> volumesBefore.stream().anyMatch(v -> v.getName().equals(after.getName())));
		assertTrue(volumesAfter.isEmpty(), "test leaked volume");

		mojo(new RemoveMojo()).execute();
	}

	private <T extends K3sMojo> T mojo(T mojo) {
		mojo.setLog(new SystemStreamLog());
		return mojo;
	}

	private void assertPodRunning() {
		var response = assertDoesNotThrow(() -> HttpClient.newHttpClient().send(HttpRequest.newBuilder()
				.GET()
				.uri(URI.create("http://localhost:8080"))
				.version(Version.HTTP_1_1)
				.build(), HttpResponse.BodyHandlers.ofString()));
		assertEquals(200, response.statusCode(), "status");
		assertTrue(response.body().startsWith("Request served by echo"), "body");
	}
}
