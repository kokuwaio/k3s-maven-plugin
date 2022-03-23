package io.kokuwa.maven.k3s.util;

import com.github.dockerjava.api.model.Container;

public enum DockerState {

	CREATED,
	RESTARTING,
	RUNNING,
	PAUSED,
	EXITED,
	DEAD;

	public static DockerState valueOf(Container container) {
		return DockerState.valueOf(container.getState().toUpperCase());
	}
}
