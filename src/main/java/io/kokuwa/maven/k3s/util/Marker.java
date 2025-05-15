package io.kokuwa.maven.k3s.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;

/**
 * Class to handle markers as communication between mojo executions.
 *
 * @author stephan@schnabel.org
 * @since 1.3.0
 */
public class Marker {

	private final Path startedMarker;

	public Marker(File directory) {
		this.startedMarker = directory.toPath().resolve("started");
	}

	public void writeStarted() throws MojoExecutionException {
		try {
			Files.createDirectories(startedMarker.getParent());
			Files.write(startedMarker, new byte[0]);
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to write marker to " + startedMarker, e);
		}
	}

	public boolean consumeStarted() throws MojoExecutionException {
		try {
			return Files.deleteIfExists(startedMarker);
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to delete marker at " + startedMarker, e);
		}
	}
}
