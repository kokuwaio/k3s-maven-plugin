package io.kokuwa.maven.k3s.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
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

	protected final Log log = new SystemStreamLog();
	protected final Docker docker = new Docker(log);

	@BeforeEach
	@AfterEach
	void reset() {
		docker.getContainer().ifPresent(docker::removeContainer);
		docker.removeVolume();
		docker.removeImage(helloWorld());
	}

	public static String helloWorld() {
		return "hello-world:linux";
	}

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
