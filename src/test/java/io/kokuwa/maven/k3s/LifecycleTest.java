package io.kokuwa.maven.k3s;

import java.nio.file.Path;

import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.kokuwa.maven.k3s.mojo.KubectlMojo;
import io.kokuwa.maven.k3s.mojo.PullMojo;
import io.kokuwa.maven.k3s.mojo.RemoveMojo;
import io.kokuwa.maven.k3s.mojo.StartMojo;
import io.kokuwa.maven.k3s.mojo.StopMojo;
import io.kokuwa.maven.test.MojoParameter;

@DisplayName("lifecycle")
public class LifecycleTest extends AbstractTest {

	@DisplayName("pull/start/kubectl/rm")
	@Test
	@MojoParameter(name = "command", value = "kubectl apply -f pod.yaml")
	void kubectl(PullMojo pull, StartMojo start, KubectlMojo kubectl, RemoveMojo remove) throws Exception {
		pull.execute();
		start.execute();
		kubectl.execute();
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
