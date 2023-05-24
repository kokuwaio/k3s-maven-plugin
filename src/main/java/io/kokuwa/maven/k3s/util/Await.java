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
	private Duration interval;
	private Runnable onTimeout;

	private Await(Log log, String text) {
		this.log = log;
		this.text = text;
		this.timeout = Duration.ofSeconds(60);
		this.interval = Duration.ofMillis(500);
		this.onTimeout = () -> {};
	}

	public Await interval(Duration newInterval) {
		this.interval = newInterval;
		return this;
	}

	public Await timeout(Duration newTimeout) {
		this.timeout = newTimeout;
		return this;
	}

	public Await onTimeout(Runnable newOnTimeout) {
		this.onTimeout = newOnTimeout;
		return this;
	}

	public void until(Callable<Boolean> check) throws MojoExecutionException {
		until(check, Function.identity());
	}

	public <V> V until(Callable<V> supplier, Function<V, Boolean> check) throws MojoExecutionException {

		Exception lastException = null;
		var started = Instant.now().plus(timeout);
		while (Instant.now().isBefore(started)) {
			try {
				V value = supplier.call();
				if (check.apply(value)) {
					log.debug(text);
					return value;
				}
				lastException = null;
			} catch (Exception e) {
				lastException = e;
				log.debug("Await " + text + " failed with exception " + e.getMessage());
			}
			wait(interval);
		}

		if (lastException == null) {
			log.error("Await " + text + " had exception while waiting", lastException);
		}

		onTimeout.run();
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
