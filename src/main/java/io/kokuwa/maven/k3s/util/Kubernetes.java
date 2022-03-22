package io.kokuwa.maven.k3s.util;

import java.util.Set;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class Kubernetes {

	private final CoreV1Api api;

	public boolean isNodeReady() throws ApiException {
		return api
				.listNode(null, null, null, null, null, null, null, null, null, null).getItems().stream()
				.allMatch(node -> {
					var ready = node.getStatus().getConditions().stream()
							.filter(condition -> condition.getType().equals("Ready"))
							.map(condition -> Boolean.parseBoolean(condition.getStatus().strip()))
							.findAny().orElse(false);
					if (!ready) {
						log.debug("Node {} is not ready", node.getMetadata().getName());
					}
					return ready;
				});
	}

	public boolean isPodsReady() throws ApiException {
		return api
				.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null, null).getItems().stream()
				.allMatch(pod -> {
					var ready = pod.getStatus().getConditions().stream()
							.filter(condition -> condition.getType().equals("Ready"))
							.map(condition -> Boolean.parseBoolean(condition.getStatus().strip()))
							.findAny().orElse(false);
					if (!ready) {
						log.debug("Pod {} is not ready", pod.getMetadata().getName());
					}
					return ready;
				});
	}

	public Set<Integer> getHostPorts() throws ApiException {
		return api
				.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null, null).getItems().stream()
				.flatMap(pod -> pod.getSpec().getContainers().stream())
				.flatMap(container -> container.getPorts().stream())
				.filter(port -> port.getHostPort() != null)
				.map(port -> port.getHostPort())
				.collect(Collectors.toSet());
	}

	public boolean isServiceAccountReady() throws ApiException {
		return api.readNamespacedServiceAccount("default", "default", null) != null;
	}
}
