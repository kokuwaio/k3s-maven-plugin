package io.kokuwa.maven.k3s.mojo;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Mojo to apply kustomize manifests.
 */
@Mojo(name = "kustomize", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class KustomizeMojo extends KubectlMojo {

	@Override
	public void execute() throws MojoExecutionException {
		setKubectlCommand("kubectl kustomize | kubectl apply -f -");
		super.execute();
	}
}
