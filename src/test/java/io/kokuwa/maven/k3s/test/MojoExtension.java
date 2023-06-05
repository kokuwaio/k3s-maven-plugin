package io.kokuwa.maven.k3s.test;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptorBuilder;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContextException;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import io.kokuwa.maven.k3s.mojo.K3sMojo;
import io.kokuwa.maven.k3s.util.DebugLog;
import io.kokuwa.maven.k3s.util.Docker;

/**
 * JUnit extension to provide prepared mojos as test parameter.
 *
 * @author stephan.schnabel@posteo.de
 */
public class MojoExtension implements ParameterResolver, BeforeAllCallback {

	private static final String containerName = "k3s-maven-plugin";
	private static final String volumeName = "k3s-maven-plugin-junit";
	private static final Log log = new DebugLog(new SystemStreamLog(), false);
	private static final Docker docker = new Docker(containerName, volumeName, log);
	private static final Map<String, MojoDescriptor> mojoDescriptors = new HashMap<>();
	private static PluginDescriptor plugin;

	@Override
	public void beforeAll(ExtensionContext context) throws Exception {
		if (plugin == null) {

			var inputStream = MojoExtension.class.getResourceAsStream("/META-INF/maven/plugin.xml");
			if (inputStream == null) {
				throw new ExtensionContextException("Plugin descriptor not found.");
			}

			plugin = new PluginDescriptorBuilder().build(new BufferedReader(new XmlStreamReader(inputStream)));
			log.debug("Found plugin: " + plugin.getId());
			for (var mojo : plugin.getMojos()) {
				log.debug("Found mojo: " + mojo.getId());
				mojoDescriptors.put(mojo.getImplementation(), mojo);
			}
		}
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext context) {
		var type = parameterContext.getParameter().getType();
		return mojoDescriptors.containsKey(type.getName()) || type.equals(Log.class) || type.equals(Docker.class);
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext context) {

		var type = parameterContext.getParameter().getType();
		if (type.equals(Log.class)) {
			return log;
		}
		if (type.equals(Docker.class)) {
			return docker;
		}

		var mojoType = parameterContext.getParameter().getType();
		var mojoDescriptor = mojoDescriptors.get(mojoType.getName());
		var mojoId = mojoDescriptor.getId();

		try {

			log.debug(mojoId + " - create mojo");
			var mojo = (K3sMojo) mojoType.getDeclaredConstructor().newInstance();

			for (var parameter : mojoDescriptor.getParameters()) {

				var name = parameter.getName();
				var defaultValue = parameter.getDefaultValue();

				if (defaultValue != null) {
					var replacedDefaultValue = defaultValue.replace("${user.home}", System.getProperty("user.home"));
					log.debug(mojoId + "#" + name + " - set default value: " + replacedDefaultValue);
					setMojoParameterValue(mojo, name, replacedDefaultValue);
				} else if (parameter.isRequired()) {
					throw new ParameterResolutionException(
							"Failed to setup mojo " + mojoId + ". Parameter " + name + " is required.");
				} else {
					log.debug(mojoId + "#" + name + " - no value set");
				}
			}

			mojo.setLog(log);
			mojo.setContainerName(containerName);
			mojo.setVolumeName(volumeName);
			return mojo;
		} catch (ReflectiveOperationException e) {
			throw new ParameterResolutionException("Failed to setup mojo " + mojoId + ".", e);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void setMojoParameterValue(Mojo mojo, String field, String value) throws ReflectiveOperationException {

		var setter = Stream.of(mojo.getClass().getMethods())
				.filter(m -> m.getName().equalsIgnoreCase("set" + field))
				.findAny().orElseThrow(() -> new ParameterResolutionException(mojo + " missing field " + field));
		var type = setter.getParameterTypes()[0];

		if (String.class.equals(type)) {
			setter.invoke(mojo, value);
		} else if (Enum.class.isAssignableFrom(type)) {
			setter.invoke(mojo, Enum.valueOf((Class<Enum>) type, value));
		} else if (File.class.equals(type)) {
			setter.invoke(mojo, new File(value));
		} else if (boolean.class.equals(type)) {
			setter.invoke(mojo, Boolean.valueOf(value));
		} else if (int.class.equals(type)) {
			setter.invoke(mojo, Integer.valueOf(value));
		} else {
			fail(field + " has unknown type: " + type);
		}
	}
}
