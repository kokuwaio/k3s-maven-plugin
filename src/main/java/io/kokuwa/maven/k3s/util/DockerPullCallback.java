package io.kokuwa.maven.k3s.util;

import java.io.Closeable;

import org.apache.maven.plugin.logging.Log;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.PullResponseItem;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DockerPullCallback implements ResultCallback<PullResponseItem> {

	private final Log log;
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
		log.debug("Image '" + image + "' pulling image ...");
	}

	@Override
	public void onNext(PullResponseItem newResponse) {
		this.response = newResponse;
		if (response.isErrorIndicated()) {
			log.error("Image '" + image + "' failed to pull: " + response.getErrorDetail().getMessage());
		} else {
			log.debug("Image '" + image + "' has status " + response.getStatus());
		}
	}

	@Override
	public void onError(Throwable throwable) {
		log.error("Image '" + image + "' failed to pull", throwable);
	}

	@Override
	public void onComplete() {
		if (response != null && response.isPullSuccessIndicated()) {
			success = true;
			log.info("Image '" + image + "' pulled: " + response.getStatus());
		}
		completed = true;
	}

	@Override
	public void close() {}
}
