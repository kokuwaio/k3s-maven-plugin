package io.kokuwa.maven.k3s.test;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.logging.SystemStreamLog;

import io.kokuwa.maven.k3s.util.DebugLog;

/**
 * Wrapper for maven logger to capture output.
 *
 * @since 1.4.0
 */
public class TestLog extends DebugLog {

	private final Map<Level, List<CharSequence>> messages = new HashMap<>();

	public TestLog(boolean debug) {
		super(new SystemStreamLog(), debug);
	}

	public void clear() {
		messages.clear();
	}

	public List<CharSequence> getMessages(Level level) {
		return messages.getOrDefault(level, List.of());
	}

	// capture

	@Override
	public void debug(CharSequence content) {
		super.debug(content);
		messages.computeIfAbsent(Level.DEBUG, k -> new ArrayList<CharSequence>()).add(content);
	}

	@Override
	public void warn(CharSequence content) {
		super.warn(content);
		messages.computeIfAbsent(Level.WARNING, k -> new ArrayList<CharSequence>()).add(content);
	}
}
