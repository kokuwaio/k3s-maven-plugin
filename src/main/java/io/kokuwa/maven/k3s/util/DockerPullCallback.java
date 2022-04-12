package io.kokuwa.maven.k3s.util;

import java.io.Closeable;

import org.slf4j.Logger;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.PullResponseItem;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DockerPullCallback implements ResultCallback<PullResponseItem> {

	private final Logger log;
	private final String image;
	private Boolean completed = false;
	private Boolean success = false;
	private @Getter PullResponseItem response;

	public boolean isCompleted() {
		return completed;
	}

	public boolean isSuccess() {
		return success;
	}

	@Override
	public void onStart(Closeable closeable) {
		log.debug("Image '{}' pulling image ...", image);
	}

	@Override
	public void onNext(PullResponseItem newResponse) {
		this.response = newResponse;
		if (response.isErrorIndicated()) {
			log.error("Image '{}' failed to pull: {}", image, response.getStatus());
		} else {
			log.trace("Image '{}' has status {}", image, response.getStatus());
		}
	}

	@Override
	public void onError(Throwable throwable) {
		log.error("Image '{}' failed to pull", image, throwable);
	}

	@Override
	public void onComplete() {
		if (response != null && response.isPullSuccessIndicated()) {
			success = true;
			log.info("Image '{}' pulled: {}", image, response.getStatus());
		}
		completed = true;
	}

	@Override
	public void close() {}
}
