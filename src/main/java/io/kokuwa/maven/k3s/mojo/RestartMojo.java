package io.kokuwa.maven.k3s.mojo;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Mojo for kubectl rollout restart.
 *
 * @author stephan.schnabel@posteo.de
 * @since 1.1.0
 */
@Mojo(name = "restart", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class RestartMojo extends K3sMojo {

	private final Pattern resourcePattern = Pattern.compile("^"
			+ "(?<kind>deployment|statefulset|daemonset)"
			+ "(/(?<namespace>[0-9a-z\\-]{1,63}))?"
			+ "/(?<name>[0-9a-z\\-]{1,63})$");

	/**
	 * Deployments to restart. Valid patterns:
	 * <ul>
	 * <li>deployment/my-namespace/my-deployment</li>
	 * <li>statefulset/my-namespace/my-deployment</li>
	 * <li>deamonset/my-namespace/my-deployment</li>
	 * <li>deployment/my-deployment</li> (in default namespace)
	 * </ul>
	 *
	 * @since 1.1.0
	 */
	@Parameter(property = "k3s.resources")
	private Set<String> resources = new HashSet<>();

	/**
	 * Timeout in seconds to wait for resources getting ready.
	 *
	 * @since 1.1.0
	 */
	@Parameter(property = "k3s.timeout", defaultValue = "300")
	private Duration timeout;

	/**
	 * Skip restarting kubectl resources.
	 *
	 * @since 1.1.0
	 */
	@Parameter(property = "k3s.skipRestart", defaultValue = "false")
	private boolean skipRestart;

	@Override
	public void execute() throws MojoExecutionException {

		if (isSkip(skipRestart)) {
			return;
		}

		// check marker, if marker is found skip restart

		if (getMarker().consumeStarted()) {
			return;
		}

		// skip if no resources configured

		if (resources.isEmpty()) {
			return;
		}

		// verify container

		if (getDocker().getContainer().isEmpty()) {
			throw new MojoExecutionException("No k3s container found");
		}

		// get callables that restarts stuff

		var tasks = resources.stream().map(this::restart).collect(Collectors.toSet());

		// execute callables

		try {
			var success = true;
			for (var future : Executors.newWorkStealingPool().invokeAll(tasks)) {
				success &= future.get();
			}
			if (!success) {
				throw new MojoExecutionException("Failed to restart resources, see previous log");
			}
		} catch (InterruptedException | ExecutionException e) {
			throw new MojoExecutionException("Failed to restart resources", e);
		}
	}

	private Callable<Boolean> restart(String resoure) {

		var matcher = resourcePattern.matcher(resoure);
		if (!matcher.matches()) {
			log.error("Failed to parse resoure: {}", resoure);
			return () -> false;
		}

		var kind = matcher.group("kind");
		var namespace = Optional.ofNullable(matcher.group("namespace")).orElse("default");
		var name = matcher.group("name");

		return () -> {
			try {
				getDocker().exec("kubectl", "rollout", "restart", kind, name, "--namespace=" + namespace);
				log.info("{} {}/{} restarted", kind, namespace, name);
				getDocker().exec("kubectl", "rollout", "status", kind, name, "--namespace=" + namespace,
						"--timeout=" + timeout.getSeconds() + "s");
				log.info("{} {}/{} restart finished", kind, namespace, name);
				return true;
			} catch (MojoExecutionException e) {
				getDocker().exec("kubectl", "get", "--output=yaml", "--namespace=" + namespace, kind, name);
				return false;
			}
		};
	}

	// setter

	public void setResources(List<String> resources) {
		this.resources = Set.copyOf(resources);
	}

	public void setTimeout(int timeout) {
		this.timeout = Duration.ofSeconds(timeout);
	}

	public void setSkipRestart(boolean skipRestart) {
		this.skipRestart = skipRestart;
	}
}
