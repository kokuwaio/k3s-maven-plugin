package io.kokuwa.maven.k3s.util;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;
import org.slf4j.Logger;

/**
 * Wrapper for process with handling.
 *
 * @author stephan@schnabel.org
 * @since 2.0.0
 */
public record DockerExecResult(Logger log, String[] command, long exitCode, List<String> messages) {

	public List<String> verify() throws MojoExecutionException {
		if (exitCode != 0) {
			var commandStr = Stream.of(command).collect(Collectors.joining(" "));
			log.error(">>> {}", commandStr);
			messages.forEach(line -> log.error("<<< {}", line));
			throw new MojoExecutionException("Command failed with exit code " + exitCode + ": " + commandStr);
		}
		return messages;
	}
}
