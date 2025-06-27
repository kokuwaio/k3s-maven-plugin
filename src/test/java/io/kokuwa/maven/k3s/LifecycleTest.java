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
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.mojo.ApplyMojo;
import io.kokuwa.maven.k3s.mojo.ImageMojo;
import io.kokuwa.maven.k3s.mojo.RemoveMojo;
import io.kokuwa.maven.k3s.mojo.RunMojo;
import io.kokuwa.maven.k3s.test.AbstractTest;

/**
 * Test fof all mojos to simulate test lifecycle.
 *
 * @author stephan@schnabel.org
 */
@DisplayName("lifecycle")
public class LifecycleTest extends AbstractTest {

	@Test
	void lifecycle(RunMojo runMojo, ImageMojo imageMojo, ApplyMojo applyMojo, RemoveMojo removeMojo) {

		applyMojo.setManifests(new File("src/test/k3s/pod.yaml"));
		runMojo.setPortBindings(List.of("8080:8080"));
		imageMojo.setCtrImages(List.of("jmalloc/echo-server:0.3.1"));

		assertDoesNotThrow(runMojo::execute);
		assertDoesNotThrow(imageMojo::execute);
		assertDoesNotThrow(applyMojo::execute);

		log.info("Request: http://" + host + ":8080");
		var response = assertDoesNotThrow(() -> HttpClient.newHttpClient().send(HttpRequest.newBuilder()
				.GET()
				.uri(URI.create("http://" + host + ":8080"))
				.version(Version.HTTP_1_1)
				.timeout(Duration.ofSeconds(2))
				.build(), HttpResponse.BodyHandlers.ofString()));
		assertEquals(200, response.statusCode(), "status");
		assertTrue(response.body().startsWith("Request served by echo"), "body");

		assertDoesNotThrow(removeMojo::execute);
	}
}
