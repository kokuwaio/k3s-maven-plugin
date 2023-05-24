package io.kokuwa.maven.k3s.util;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.logging.Log;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DockerLogCallback implements ResultCallback<Frame> {

	@Getter
	private final List<String> messages = new ArrayList<>();
	private final Log log;
	private boolean completed;

	public boolean isCompleted() {
		return completed;
	}

	public void replayOnWarn() {
		messages.forEach(log::warn);
	}

	@Override
	public void onNext(Frame frame) {
		var message = new String(frame.getPayload()).strip();
		messages.add(message);
		log.debug(message);
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
