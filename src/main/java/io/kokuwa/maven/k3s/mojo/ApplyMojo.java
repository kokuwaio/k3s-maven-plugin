package io.kokuwa.maven.k3s.mojo;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.kokuwa.maven.k3s.KubectlMojo;
import lombok.Getter;
import lombok.Setter;

/**
 * Mojo to apply kustomize manifests.
 */
@Mojo(name = "apply", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class ApplyMojo extends KubectlMojo {

	@Parameter(property = "k3s.kubectl.command", defaultValue = "kubectl apply -f .")
	@Getter
	@Setter
	private String command = "kubectl apply -f .";
}
