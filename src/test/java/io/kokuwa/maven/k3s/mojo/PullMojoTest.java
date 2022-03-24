package io.kokuwa.maven.k3s.mojo;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.AbstractTest;
import io.kokuwa.maven.test.MojoParameter;

@DisplayName("mojo: pull")
public class PullMojoTest extends AbstractTest {

	@DisplayName("defaults")
	@Test
	void defaults(PullMojo mojo) throws Exception {
		mojo.execute();
	}

	@DisplayName("pullAlways")
	@Test
	@MojoParameter(name = "pullAlways", value = "true")
	void pullAlways(PullMojo mojo) throws Exception {
		mojo.execute();
	}

	@DisplayName("pullAdditionalImages")
	@Test
	void pullAdditionalImages(PullMojo mojo) throws Exception {
		mojo.setPullAdditionalImages(List.of(
				"gcr.io/distroless/java:11",
				"quay.io/strimzi/kafka:latest-kafka-2.8.1",
				"rancher/mirrored-coredns-coredns:1.8.6",
				"rancher/mirrored-pause:3.6",
				"kubernetesui/dashboard:v2.4.0",
				"jmalloc/echo-server:0.3.1",
				"obsidiandynamics/kafdrop:3.28.0",
				"postgres:14.1-alpine",
				"traefik:v2.6.0"));
		mojo.execute();
	}
}
