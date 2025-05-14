package io.kokuwa.maven.k3s.mojo;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Mojo for copying files to docker container.
 *
 * @author stephan.schnabel@posteo.de
 * @since 1.6.0
 */
@Mojo(name = "copy", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class CopyMojo extends K3sMojo {

	/**
	 * Source path on host to copy to docker container.
	 *
	 * @since 1.6.0
	 */
	@Parameter(property = "k3s.copySource")
	private Path copySource;

	/**
	 * Target path in docker container.
	 *
	 * @since 1.6.0
	 */
	@Parameter(property = "k3s.copyTarget")
	private Path copyTarget;

	/**
	 * Skip copying files.
	 *
	 * @since 1.6.0
	 */
	@Parameter(property = "k3s.skipCopy", defaultValue = "false")
	private boolean skipCopy;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipCopy)) {
			return;
		}
		if (!Files.exists(copySource)) {
			throw new MojoExecutionException("Path " + copySource + " not found.");
		}
		if (getDocker().getContainer().isEmpty()) {
			throw new MojoExecutionException("No k3s container found");
		}
		getDocker().copyToContainer(copySource, toLinuxPath(copyTarget), Duration.ofMinutes(5));
	}

	// setter

	public void setCopySource(File copySource) {
		this.copySource = copySource == null ? null : copySource.toPath();
	}

	public void setCopyTarget(File copyTarget) {
		this.copyTarget = copyTarget == null ? null : copyTarget.toPath();
	}

	public void setSkipCopy(boolean skipCopy) {
		this.skipCopy = skipCopy;
	}
}
