package io.kokuwa.maven.k3s.util;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;

/**
 * Consumes logs from docker.
 *
 * @author stephan@schnabel.org
 * @since 2.0.0
 */
public class DockerLogCallback implements ResultCallback<Frame> {

	private static final Logger log = LoggerFactory.getLogger(DockerPullCallback.class);

	public final List<String> messages = new ArrayList<>();
	private StringBuffer sb = new StringBuffer();
	private boolean completed;

	public boolean isCompleted() {
		return completed;
	}

	public void replayOnWarn() {
		messages.forEach(log::warn);
	}

	// methods

	@Override
	public void onStart(Closeable closeable) {}

	@Override
	public synchronized void onNext(Frame frame) {
		var text = new String(frame.getPayload());
		if (text.endsWith("\n")) {
			if (!sb.isEmpty()) {
				text = sb.append(text).toString();
				sb = new StringBuffer();
			}
			line(text);
		} else {
			sb.append(text);
		}
	}

	@Override
	public void onError(Throwable throwable) {}

	@Override
	public void onComplete() {
		completed = true;
		line(sb.toString());
	}

	@Override
	public void close() {}

	private void line(String line) {
		for (var tmp : line.split("\n")) {
			var strippedLine = tmp.strip();
			if (!strippedLine.isBlank()) {
				log.debug(strippedLine);
				messages.add(strippedLine);
			}
		}
	}
}
