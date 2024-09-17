package io.kokuwa.maven.k3s.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptorBuilder;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.opentest4j.AssertionFailedError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kokuwa.maven.k3s.mojo.K3sMojo;
import io.kokuwa.maven.k3s.util.Docker;

/**
 * JUnit extension to provide prepared mojos as test parameter.
 *
 * @author stephan.schnabel@posteo.de
 */
public class MojoExtension implements ParameterResolver, BeforeAllCallback {

	private static final String containerName = "k3s-maven-plugin";
	private static final String volumeName = "k3s-maven-plugin-junit";
	private static final Logger log = LoggerFactory.getLogger(MojoExtension.class);
	private static final Docker docker = new Docker(containerName, volumeName, log);
	private static final Set<MojoDescriptor> mojos = new HashSet<>();

	@Override
	public void beforeAll(ExtensionContext context) throws Exception {
		if (mojos.isEmpty()) {
			try (var inputStream = K3sMojo.class.getResourceAsStream("/META-INF/maven/plugin.xml");) {
				assertNotNull(inputStream, "Plugin descriptor for not found, run 'mvn plugin:descriptor'.");
				new PluginDescriptorBuilder()
						.build(new StringReader(new String(inputStream.readAllBytes())
								.replaceAll("\\$\\{project.build.directory}", "target")
								.replaceAll("\\$\\{project.basedir}", ".")))
						.getMojos().forEach(mojos::add);
			}
		}
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext context) {
		var type = parameterContext.getParameter().getType();
		return mojos.stream().map(MojoDescriptor::getImplementation).anyMatch(type.getName()::equals)
				|| type.equals(Logger.class)
				|| type.equals(Docker.class);
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext context) {

		var type = parameterContext.getParameter().getType();
		if (type.equals(Logger.class)) {
			return log;
		}
		if (type.equals(Docker.class)) {
			return docker;
		}

		var descriptor = mojos.stream().filter(m -> m.getImplementation().equals(type.getName())).findAny().get();
		try {
			var mojo = (K3sMojo) type.getDeclaredConstructor().newInstance();
			for (var parameter : descriptor.getParameters()) {
				if (parameter.getDefaultValue() != null) {
					var setter = Stream.of(type.getMethods())
							.filter(m -> m.getName().toLowerCase().equals("set" + parameter.getName().toLowerCase()))
							.findFirst()
							.orElseThrow(() -> new AssertionFailedError("Setter not found: set" + parameter.getName()));
					var parameterType = setter.getParameters()[0].getType();
					if (String.class.equals(parameterType)) {
						setter.invoke(mojo, parameter.getDefaultValue());
					} else if (File.class.equals(parameterType)) {
						setter.invoke(mojo, new File(parameter.getDefaultValue()));
					} else if (boolean.class.equals(parameterType)) {
						setter.invoke(mojo, Boolean.valueOf(parameter.getDefaultValue()));
					} else if (int.class.equals(parameterType)) {
						setter.invoke(mojo, Integer.valueOf(parameter.getDefaultValue()));
					} else {
						fail(parameter.getName() + " has unknown type: " + type);
					}
				}
			}
			mojo.setContainerName(containerName);
			mojo.setVolumeName(volumeName);
			return mojo;
		} catch (ReflectiveOperationException e) {
			throw new ParameterResolutionException("Failed to setup mojo " + descriptor + ".", e);
		}
	}
}
