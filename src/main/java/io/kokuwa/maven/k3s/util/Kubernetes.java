package io.kokuwa.maven.k3s.util;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Kubernetes {

	private final CoreV1Api api;

	public boolean isNodeReady() throws ApiException {
		return api
				.listNode(null, null, null, null, null, null, null, null, null, null)
				.getItems().stream().allMatch(node -> node
						.getStatus().getConditions().stream()
						.anyMatch(c -> c.getType().equals("Ready") && Boolean.parseBoolean(c.getStatus().strip())));
	}

	public boolean isPodsReady() throws ApiException {
		return api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null, null)
				.getItems().stream().allMatch(node -> node
						.getStatus().getConditions().stream()
						.anyMatch(c -> c.getType().equals("Ready") && Boolean.parseBoolean(c.getStatus().strip())));
	}

	public boolean isServiceAccountReady() throws ApiException {
		return api.readNamespacedServiceAccount("default", "default", null) != null;
	}
}
