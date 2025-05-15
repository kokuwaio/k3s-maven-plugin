package io.kokuwa.maven.k3s.util;

import java.io.Closeable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.PullResponseItem;

/**
 * Replacement for {@link PullImageResultCallback} because we want redirect
 * logging to maven output and format it for more readability.
 *
 * @author stephan@schnabel.org
 * @see com.github.dockerjava.api.command.PullImageResultCallback
 * @since 2.0.0
 */
public class DockerPullCallback implements ResultCallback<PullResponseItem> {

	private static final Logger log = LoggerFactory.getLogger(DockerPullCallback.class);

	private final String image;
	private boolean completed = false;
	private boolean success = false;
	private PullResponseItem response;

	DockerPullCallback(String image) {
		this.image = image;
	}

	public boolean isCompleted() {
		return completed;
	}

	public boolean isSuccess() {
		return success;
	}

	// methods

	@Override
	public void onStart(Closeable closeable) {}

	@Override
	public void onNext(PullResponseItem newResponse) {
		this.response = newResponse;
		if (newResponse.isErrorIndicated()) {
			log.error("Image {} failed to pull: {}", image, newResponse.getErrorDetail().getMessage());
		} else {
			log.debug("Image {} has status {}", image, newResponse.getStatus());
		}
	}

	@Override
	public void onError(Throwable throwable) {
		log.error("Image {} failed to pull", image, throwable);
	}

	@Override
	public void onComplete() {
		if (response != null && response.isPullSuccessIndicated()) {
			success = true;
			log.info("Image {} pulled: {}", image, response.getStatus());
		}
		completed = true;
	}

	@Override
	public void close() {}
}
