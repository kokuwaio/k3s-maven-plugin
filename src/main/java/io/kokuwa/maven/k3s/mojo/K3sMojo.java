package io.kokuwa.maven.k3s.mojo;

import java.io.File;
import java.nio.file.Path;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;

import io.kokuwa.maven.k3s.util.DebugLog;
import io.kokuwa.maven.k3s.util.Docker;
import io.kokuwa.maven.k3s.util.Marker;

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
	@Parameter(property = "k3s.debug", defaultValue = "false")
	private boolean debug;

	/**
	 * Skip plugin.
	 *
	 * @since 0.1.0
	 */
	@Parameter(property = "k3s.skip", defaultValue = "false")
	private boolean skip;

	/** Name for the create docker container. */
	@Parameter(defaultValue = "k3s-maven-plugin", readonly = true)
	private String containerName;

	/** Name for the create volume container. */
	@Parameter(defaultValue = "k3s-maven-plugin", readonly = true)
	private String volumeName;

	/** Marker for maven status stuff. */
	@Parameter(defaultValue = "${project.build.directory}/maven-status/k3s-maven-plugin", readonly = true)
	private Marker marker;

	// generic methods

	private Log log;
	private Docker docker;

	public boolean isSkip(boolean skipMojo) {
		return skip || skipMojo;
	}

	public Marker getMarker() {
		return marker;
	}

	@Override
	public Log getLog() {
		return log == null ? log = new DebugLog(super.getLog(), debug) : log;
	}

	public Docker getDocker() {
		return docker == null ? docker = new Docker(containerName, volumeName, getLog()) : docker;
	}

	public String toLinuxPath(Path path) {
		// ugly hack for windows - docker path inside k3s needs to be a kinux path
		return path.toString().replace("\\", "/");
	}

	// setter

	public void setMarker(File directory) {
		this.marker = new Marker(directory);
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public void setSkip(boolean skip) {
		this.skip = skip;
	}

	public void setContainerName(String containerName) {
		this.containerName = containerName;
	}

	public void setVolumeName(String volumeName) {
		this.volumeName = volumeName;
	}
}
