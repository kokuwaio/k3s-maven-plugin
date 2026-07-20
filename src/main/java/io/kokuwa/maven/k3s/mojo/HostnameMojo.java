package io.kokuwa.maven.k3s.mojo;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Optional;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.WaitContainerCondition;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import io.kokuwa.maven.k3s.util.Await;
import io.kokuwa.maven.k3s.util.DockerLogCallback;

/**
 * Determine docker daemon hostname and set into Project.
 *
 * @author stephan@schnabel.org
 * @since 2.3.0
 */
@Mojo(name = "hostname", defaultPhase = LifecyclePhase.VALIDATE)
public class HostnameMojo extends K3sImageMojo {

	private Map<String, String> env = System.getenv();

	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project;

	/**
	 * Command to determine hostname.
	 *
	 * @since 2.3.0
	 */
	@Parameter(property = "k3s.hostnameCommand", defaultValue = "ip -4 -o addr show|grep -v 127.0.0.1|grep -v 172.17.0.1|tr -s ' '|cut -d' ' -f4|cut -d'/' -f1")
	private String hostnameCommand;

	/**
	 * Property name where to store the hostname.
	 *
	 * @since 2.3.0
	 */
	@Parameter(property = "k3s.hostnameProperty", defaultValue = "k3s.hostname")
	private String hostnameProperty;

	/**
	 * Skip hostname detection.
	 *
	 * @since 2.3.0
	 */
	@Parameter(property = "k3s.skipHostname", defaultValue = "false")
	private boolean skipHostname;

	@SuppressWarnings("resource")
	@Override
	public void execute() throws MojoExecutionException {
		if (isSkip(skipHostname)) {
			return;
		}

		var dockerHostEnv = env.get(DefaultDockerClientConfig.DOCKER_HOST);
		if (dockerHostEnv != null) {
			var hostname = Optional
					.of(dockerHostEnv)
					.map(URI::create)
					.filter(uri -> "tcp".equals(uri.getScheme()))
					.map(URI::getHost)
					.map(s -> {
						try {
							return InetAddress.getByName(s).getHostAddress();
						} catch (UnknownHostException e) {
							return s;
						}
					})
					.orElse(null);

			if (hostname == null) {
				log.debug("Could not extract host from {}={}", DefaultDockerClientConfig.DOCKER_HOST, dockerHostEnv);
			} else {
				log.info("Use host {} from {}={}", hostname, DefaultDockerClientConfig.DOCKER_HOST, dockerHostEnv);
				project.getProperties().put(hostnameProperty, hostname);
				return;
			}
		}

		if (hostnameCommand != null) {

			// fallback to running a docker container in network mode

			String containerId = null;
			try {
				containerId = getDocker().getClient()
						.createContainerCmd(pullImage())
						.withHostConfig(new HostConfig().withNetworkMode("host"))
						.withEntrypoint("sh", "-euc", hostnameCommand)
						.exec().getId();
				getDocker().getClient().startContainerCmd(containerId).exec();
				getDocker().getClient().waitContainerCmd(containerId)
						.withCondition(WaitContainerCondition.NOT_RUNNING)
						.start().awaitCompletion();

				var callback = getDocker().getClient()
						.logContainerCmd(containerId)
						.withStdOut(true)
						.withStdErr(true)
						.withFollowStream(true)
						.exec(new DockerLogCallback());
				Await.await(log, containerId).until(() -> callback.isCompleted());

				if (callback.messages.isEmpty()) {
					throw new MojoExecutionException("Failed to determine hostname, docker approach returned nothing.");
				} else if (callback.messages.size() != 1) {
					log.warn("Docker approach returned multiple hostnames: {}", callback.messages);
				}

				log.info("Use host {} from docker approach", callback.messages.get(0));
				project.getProperties().put(hostnameProperty, callback.messages.get(0));
				return;

			} catch (InterruptedException e) {
				throw new MojoExecutionException("Failed to determine hostname", e);
			} finally {
				getDocker().getClient().removeContainerCmd(containerId).withForce(true).exec();
			}
		}

		throw new MojoExecutionException("Failed to determine hostname");
	}

	// setter

	public void setProject(MavenProject project) {
		this.project = project;
	}

	public void setHostnameProperty(String hostnameProperty) {
		this.hostnameProperty = hostnameProperty;
	}

	public void setSkipHostname(boolean skipHostname) {
		this.skipHostname = skipHostname;
	}

	public void setHostnameCommand(String hostnameCommand) {
		this.hostnameCommand = hostnameCommand;
	}

	public void setEnv(Map<String, String> env) {
		this.env = env;
	}
}
