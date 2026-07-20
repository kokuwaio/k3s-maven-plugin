package io.kokuwa.maven.k3s.mojo;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Mojo for debugging k3s.
 *
 * @author stephan@schnabel.org
 * @since 2.2.0
 */
@Mojo(name = "debug", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST, requiresProject = false)
public class DebugMojo extends K3sDebugMojo {

	/**
	 * Skip applying debug manifests.
	 *
	 * @since 2.2.0
	 */
	@Parameter(property = "k3s.skipDebug", defaultValue = "false")
	private boolean skipDebug;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipDebug)) {
			return;
		}

		var container = getDocker().getContainer().orElseThrow(() -> new MojoExecutionException("No container found"));
		handleDebugInfos(container);
	}

	// setter

	public void setSkipDebug(boolean skipDebug) {
		this.skipDebug = skipDebug;
	}
}
