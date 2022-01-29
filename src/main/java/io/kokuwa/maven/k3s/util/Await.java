package io.kokuwa.maven.k3s.util;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.function.Function;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

/**
 * Utility for waits.
 *
 * Alternatives:
 * <ul>
 * <li><a>https://github.com/rnorth/duct-tape</a> - dead project?</li>
 * <li><a>https://github.com/awaitility/awaitility</a> - has junit dependency</li>
 * </ul>
 */
public class Await {

	public static Await await(Log log, String text) {
		return new Await(log, text);
	}

	private final Log log;
	private final String text;
	private Duration timeout;
	private Duration pollDelay;
	private Duration pollInterval;

	private Await(Log log, String text) {
		this.log = log;
		this.text = text;
		this.timeout = Duration.ofSeconds(60);
		this.pollDelay = Duration.ofSeconds(1);
		this.pollInterval = Duration.ofSeconds(1);
	}

	public Await pollDelay(Duration newPollDelay) {
		this.pollDelay = newPollDelay;
		return this;
	}

	public Await pollInterval(Duration newPollInterval) {
		this.pollInterval = newPollInterval;
		return this;
	}

	public Await timeout(Duration newTimeout) {
		this.timeout = newTimeout;
		return this;
	}

	public void until(Callable<Boolean> check) throws MojoExecutionException {
		until(check, Function.identity());
	}

	public <V> V until(Callable<V> supplier, Function<V, Boolean> check) throws MojoExecutionException {

		wait(pollDelay);

		var started = Instant.now().plus(timeout);
		while (Instant.now().isBefore(started)) {
			wait(pollInterval);
			try {
				V value = supplier.call();
				if (check.apply(value)) {
					log.debug(text + " finished");
					return value;
				}
			} catch (Exception e) {}
		}

		throw new MojoExecutionException(text + " did not complete in " + timeout.toSeconds() + " seconds");
	}

	private void wait(Duration duration) throws MojoExecutionException {
		try {
			Thread.sleep(duration.toMillis());
		} catch (InterruptedException e) {
			throw new MojoExecutionException(text + " interrupted");
		}
	}
}
