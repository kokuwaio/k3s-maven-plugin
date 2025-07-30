package io.kokuwa.maven.k3s.mojo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Mojo to remove k3s container.
 *
 * @author stephan@schnabel.org
 * @since 0.1.0
 */
@Mojo(name = "rm", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST, requiresProject = false)
public class RemoveMojo extends K3sMojo {

	/**
	 * Delete not only docker container, delete also cached data.
	 *
	 * @since 0.3.0
	 */
	@Parameter(property = "k3s.includeCache", defaultValue = "false")
	private boolean includeCache;

	/**
	 * Skip remove of k3s container.
	 *
	 * @since 0.1.0
	 */
	@Parameter(property = "k3s.skipRm", defaultValue = "false")
	private boolean skipRm;

	/**
	 * Path where kubeconfig.yaml was be placed on host.
	 *
	 * @since 2.0.1
	 */
	@Parameter(property = "k3s.kubeconfig", defaultValue = "${project.build.directory}/k3s.yaml")
	private Path kubeconfig;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipRm)) {
			return;
		}

		getDocker().getContainer().ifPresent(getDocker()::remove);
		try {
			Files.deleteIfExists(kubeconfig);
		} catch (IOException e) {
			log.warn("Failed to delete {}", kubeconfig);
		}
		if (includeCache) {
			getDocker().removeVolume();
			log.info("Deleted cache volume.");
		}
	}

	// setter

	public void setKubeconfig(File kubeconfig) {
		this.kubeconfig = kubeconfig.toPath().toAbsolutePath();
	}

	public void setIncludeCache(boolean includeCache) {
		this.includeCache = includeCache;
	}

	public void setSkipRm(boolean skipRm) {
		this.skipRm = skipRm;
	}
}
