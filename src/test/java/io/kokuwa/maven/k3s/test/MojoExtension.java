package io.kokuwa.maven.k3s.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptorBuilder;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.InterpolationFilterReader;
import org.codehaus.plexus.util.ReflectionUtils;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

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
	private static final TestLog log = new TestLog(false);
	private static final Docker docker = new Docker(containerName, volumeName, log);
	private static final Set<MojoDescriptor> mojos = new HashSet<>();

	@Override
	public void beforeAll(ExtensionContext context) throws Exception {
		if (mojos.isEmpty()) {
			var inputStream = K3sMojo.class.getResourceAsStream("/META-INF/maven/plugin.xml");
			assertNotNull(inputStream, "Plugin descriptor for not found, run 'mvn plugin:descriptor'.");
			new PluginDescriptorBuilder()
					.build(new InterpolationFilterReader(new XmlStreamReader(inputStream),
							Map.of("project.build.directory", "target", "project.basedir", ".")))
					.getMojos()
					.forEach(mojos::add);
		}
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext context) {
		var type = parameterContext.getParameter().getType();
		return mojos.stream().map(MojoDescriptor::getImplementation).anyMatch(type.getName()::equals)
				|| type.equals(Log.class)
				|| type.equals(TestLog.class)
				|| type.equals(Docker.class);
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext context) {

		var type = parameterContext.getParameter().getType();
		if (type.equals(Log.class) || type.equals(TestLog.class)) {
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
					var setter = ReflectionUtils.getSetter(parameter.getName(), mojo.getClass());
					var parameterType = ReflectionUtils.getSetterType(setter);
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
			mojo.setLog(log);
			mojo.setContainerName(containerName);
			mojo.setVolumeName(volumeName);
			return mojo;
		} catch (ReflectiveOperationException e) {
			throw new ParameterResolutionException("Failed to setup mojo " + descriptor + ".", e);
		}
	}
}
