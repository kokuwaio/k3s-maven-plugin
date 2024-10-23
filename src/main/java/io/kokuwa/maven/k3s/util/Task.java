package io.kokuwa.maven.k3s.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.slf4j.Logger;

/**
 * Wrapper for process with handling.
 *
 * @author stephan.schnabel@posteo.de
 * @since 1.0.0
 */
public class Task {

	private final Logger log;
	private final List<String> command;
	private Duration timeout;

	private Process process;
	private final List<String> output = new ArrayList<>();
	private final List<Thread> threads = new ArrayList<>();

	private Task(Logger log, Duration timeout, List<String> command) {
		this.log = log;
		this.command = command;
		this.timeout = timeout;
	}

	// config

	public static Task of(Logger log, Duration timeout, String... command) {
		return new Task(log, timeout, List.of(command));
	}

	public static Task of(Logger log, Duration timeout, List<String> command) {
		return new Task(log, timeout, command);
	}

	public List<String> command() {
		return command;
	}

	public Duration timeout() {
		return timeout;
	}

	public Task timeout(Duration newTimeout) {
		this.timeout = newTimeout;
		return this;
	}

	@Override
	public String toString() {
		return command.stream().collect(Collectors.joining(" ")) + " (timeout: " + timeout + ")";
	}

	// execute

	public List<String> run() throws MojoExecutionException {
		return start().waitFor().verify().output();
	}

	public Task start() throws MojoExecutionException {

		log.debug(">>> {}", this);

		try {
			var builder = new ProcessBuilder(command);
			process = builder.start();
			collectLogs("stdout", process.getInputStream());
			collectLogs("stderr", process.getErrorStream());
		} catch (IOException e) {
			throw new MojoExecutionException("Command failed: " + this, e);
		}

		return this;
	}

	public Task waitFor() throws MojoExecutionException {
		try {
			if (!process.waitFor(timeout.getSeconds(), TimeUnit.SECONDS)) {
				throw new MojoExecutionException("Timeout failed: " + this);
			}
		} catch (InterruptedException e) {
			throw new MojoExecutionException("Command failed: " + this, e);
		} finally {
			Await.await(log, "threads finished").until(() -> threads.stream().noneMatch(Thread::isAlive));
			close();
		}
		return this;
	}

	public Task verify() throws MojoExecutionException {
		var exitCode = process.exitValue();
		if (exitCode != 0) {
			log.error(">>> {}", this);
			output.forEach(line -> log.error("<<< {}", line));
			throw new MojoExecutionException("Command failed with exit code " + exitCode + ": " + this);
		}
		return this;
	}

	public int exitCode() {
		return process.exitValue();
	}

	public List<String> output() {
		return output;
	}

	public void close() {
		process.destroyForcibly();
		threads.forEach(Thread::interrupt);
	}

	private void collectLogs(String stream, InputStream inputStream) {
		var thread = new Thread(() -> {
			try (var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					output.add(line);
					log.debug("<<< [{}] {}", stream, line);
				}
			} catch (IOException e) {
				log.debug("Stream {} closed unexpected: {}", stream, e.getMessage());
			}
		});
		thread.start();
		threads.add(thread);
	}
}
