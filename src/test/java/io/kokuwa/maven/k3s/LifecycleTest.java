package io.kokuwa.maven.k3s;

import java.nio.file.Path;
import java.util.List;

import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.mojo.ApplyMojo;
import io.kokuwa.maven.k3s.mojo.KustomizeMojo;
import io.kokuwa.maven.k3s.mojo.PullMojo;
import io.kokuwa.maven.k3s.mojo.RemoveMojo;
import io.kokuwa.maven.k3s.mojo.StartMojo;
import io.kokuwa.maven.k3s.mojo.StopMojo;
import io.kokuwa.maven.test.MojoParameter;

@DisplayName("lifecycle")
public class LifecycleTest extends AbstractTest {

	@DisplayName("pull/start/apply/rm")
	@Test
	@MojoParameter(name = "command", value = "kubectl apply -f pod.yaml")
	void kubectl(PullMojo pull, StartMojo start, ApplyMojo apply, RemoveMojo remove) throws Exception {
		pull.execute();
		start.setPortBindings(List.of("8080:8080")).execute();
		apply.setCommand("kubectl apply -f pod.yaml").execute();
		assertPodRunning();
		remove.execute();
	}

	@DisplayName("pull/start/kustomize/rm")
	@Test
	void kustomize(PullMojo pull, StartMojo start, KustomizeMojo kustomize, RemoveMojo remove) throws Exception {
		pull.execute();
		start.setPortBindings(List.of("8080:8080")).execute();
		kustomize.execute();
		assertPodRunning();
		remove.execute();
	}

	@DisplayName("pull/start/start/stop/start/start/rm")
	@Test
	void restart(PullMojo pull, StartMojo start, StopMojo stop, RemoveMojo remove) throws Exception {
		pull.execute();
		start.execute();

		// restart again
		start.execute();

		// restart again with missing kubeconfig
		FileUtils.forceDelete(Path.of("target/k3s/kubeconfig.yaml").toFile());
		start.execute();

		// restart stopped
		stop.execute();
		start.execute();

		remove.execute();
	}
}
