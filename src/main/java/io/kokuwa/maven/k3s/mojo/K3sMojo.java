package io.kokuwa.maven.k3s.mojo;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;

import io.kokuwa.maven.k3s.util.DebugLog;
import io.kokuwa.maven.k3s.util.Docker;
import lombok.Setter;

/**
 * Base class for all mojos of this plugin.
 *
 * @author stephan.schnabel@posteo.de
 * @since 0.1.0
 */
public abstract class K3sMojo extends AbstractMojo {

	/**
	 * Enable debuging of docker and k3s logs.
	 *
	 * @since 1.0.0
	 */
	@Setter @Parameter(property = "k3s.debug", defaultValue = "false")
	private boolean debug;

	/**
	 * Skip plugin.
	 *
	 * @since 0.1.0
	 */
	@Setter @Parameter(property = "k3s.skip", defaultValue = "false")
	private boolean skip;

	/** Name for the create docker container. */
	@Setter @Parameter(defaultValue = "k3s-maven-plugin", readonly = true)
	private String containerName;

	/** Name for the create volume container. */
	@Setter @Parameter(defaultValue = "k3s-maven-plugin", readonly = true)
	private String volumeName;

	// generic methods

	private Log log;
	private Docker docker;

	public boolean isSkip(boolean skipMojo) {
		return skip || skipMojo;
	}

	@Override
	public Log getLog() {
		return log == null ? log = new DebugLog(super.getLog(), debug) : log;
	}

	public Docker getDocker() {
		return docker == null ? docker = new Docker(containerName, volumeName, getLog()) : docker;
	}
}
