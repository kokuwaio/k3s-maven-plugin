package io.kokuwa.maven.k3s.util;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1NodeCondition;
import io.kubernetes.client.openapi.models.V1PodCondition;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Kubernetes {

	private final CoreV1Api core;
	private final AppsV1Api apps;

	public Kubernetes(ApiClient client) {
		this.core = new CoreV1Api(client);
		this.apps = new AppsV1Api(client);
	}

	public boolean isNodeReady() throws ApiException {
		return core
				.listNode(null, null, null, null, null, null, null, null, null, null).getItems().stream()
				.allMatch(node -> {
					var ready = node.getStatus().getConditions().stream()
							.filter(condition -> condition.getType().equals(V1NodeCondition.TypeEnum.READY))
							.map(condition -> Boolean.parseBoolean(condition.getStatus().strip()))
							.findAny().orElse(false);
					if (!ready) {
						log.debug("Node {} is not ready", node.getMetadata().getName());
					}
					return ready;
				});
	}

	public boolean isPodsReady() throws ApiException {
		return core
				.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null, null).getItems().stream()
				.allMatch(pod -> {
					var ready = pod.getStatus().getConditions().stream()
							.filter(condition -> condition.getType().equals(V1PodCondition.TypeEnum.READY))
							.map(condition -> Boolean.parseBoolean(condition.getStatus().strip()))
							.findAny().orElse(false);
					if (!ready) {
						log.debug("Pod {} is not ready", pod.getMetadata().getName());
					}
					return ready;
				});
	}

	public boolean isDeploymentsReady() throws ApiException {
		return apps
				.listDeploymentForAllNamespaces(null, null, null, null, null, null, null, null, null, null)
				.getItems().stream()
				.allMatch(deployment -> {
					var ready = deployment.getStatus().getConditions().stream()
							.filter(condition -> condition.getType().equalsIgnoreCase("Available"))
							.map(condition -> Boolean.parseBoolean(condition.getStatus().strip()))
							.findAny().orElse(false);
					if (!ready) {
						log.debug("Deployment {} is not ready", deployment.getMetadata().getName());
					}
					return ready;
				});
	}

	public boolean isStatefulSetsReady() throws ApiException {
		return apps
				.listStatefulSetForAllNamespaces(null, null, null, null, null, null, null, null, null, null)
				.getItems().stream()
				.allMatch(statefulSet -> {
					var ready = statefulSet.getSpec().getReplicas() == statefulSet.getStatus().getAvailableReplicas();
					if (!ready) {
						log.debug("StatefulSet {} is not ready", statefulSet.getMetadata().getName());
					}
					return ready;
				});
	}

	public boolean isServiceAccountReady() throws ApiException {
		return core.readNamespacedServiceAccount("default", "default", null) != null;
	}
}
