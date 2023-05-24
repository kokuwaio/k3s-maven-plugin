package io.kokuwa.maven.k3s.util;

import org.apache.maven.plugin.logging.Log;

/**
 * Wrapper for maven logger to enable debugging for k3s-maven-pugin without enable exhausting maven debug.
 *
 * @since 0.11.0
 */
public class DebugLog implements Log {

	private final Log log;
	private final boolean debug;

	public DebugLog(Log log, boolean debug) {
		this.log = log;
		this.debug = debug;
	}

	@Override
	public boolean isDebugEnabled() {
		return log.isDebugEnabled() || debug;
	}

	@Override
	public void debug(CharSequence content) {
		if (debug) {
			log.info(content);
		} else {
			log.debug(content);
		}
	}

	@Override
	public void debug(CharSequence content, Throwable error) {
		if (debug) {
			log.info(content, error);
		} else {
			log.debug(content, error);
		}
	}

	@Override
	public void debug(Throwable error) {
		if (debug) {
			log.info(error);
		} else {
			log.debug(error);
		}
	}

	@Override
	public boolean isInfoEnabled() {
		return log.isInfoEnabled();
	}

	@Override
	public void info(CharSequence content) {
		log.info(content);
	}

	@Override
	public void info(CharSequence content, Throwable error) {
		log.info(content, error);
	}

	@Override
	public void info(Throwable error) {
		log.info(error);
	}

	@Override
	public boolean isWarnEnabled() {
		return log.isWarnEnabled();
	}

	@Override
	public void warn(CharSequence content) {
		log.info(content);
	}

	@Override
	public void warn(CharSequence content, Throwable error) {
		log.info(content, error);
	}

	@Override
	public void warn(Throwable error) {
		log.info(error);
	}

	@Override
	public boolean isErrorEnabled() {
		return log.isErrorEnabled();
	}

	@Override
	public void error(CharSequence content) {
		log.info(content);
	}

	@Override
	public void error(CharSequence content, Throwable error) {
		log.info(content, error);
	}

	@Override
	public void error(Throwable error) {
		log.info(error);
	}
}
