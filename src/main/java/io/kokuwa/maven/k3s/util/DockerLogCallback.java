package io.kokuwa.maven.k3s.util;

import java.io.Closeable;

import org.apache.maven.plugin.logging.Log;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;

public class DockerLogCallback implements ResultCallback<Frame> {

	private final Log log;
	private final boolean streamLogs;
	private boolean completed;

	public DockerLogCallback(Log log, boolean streamLogs) {
		this.log = log;
		this.streamLogs = streamLogs;
	}

	public boolean isCompleted() {
		return completed;
	}

	@Override
	public void onNext(Frame frame) {
		var message = new String(frame.getPayload()).strip();
		if (streamLogs) {
			log.info(message);
		} else {
			log.debug(message);
		}
	}

	@Override
	public void onError(Throwable throwable) {}

	@Override
	public void onStart(Closeable closeable) {}

	@Override
	public void onComplete() {
		completed = true;
	}

	@Override
	public void close() {}
}
