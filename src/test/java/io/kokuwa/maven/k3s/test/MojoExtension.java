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

/**
 * JUnit extension to provide prepared mojos as test parameter.
 *
 * @author stephan.schnabel@posteo.de
 */
public class MojoExtension implements ParameterResolver, BeforeAllCallback {

	private static Log log = new SystemStreamLog();
	private static PluginDescriptor plugin;
	private static Map<Class<Mojo>, MojoDescriptor> mojoDescriptors = new HashMap<>();

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
				mojoDescriptors.put((Class<Mojo>) Class.forName(mojo.getImplementation()), mojo);
			}
		}
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext context) {
		return mojoDescriptors.containsKey(parameterContext.getParameter().getType());
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext context) {

		var mojoType = (Class<Mojo>) parameterContext.getParameter().getType();
		var mojoDescriptor = mojoDescriptors.get(parameterContext.getParameter().getType());
		var mojoId = mojoDescriptor.getId();

		try {

			log.debug(mojoId + " - create mojo");
			var mojo = mojoType.getDeclaredConstructor().newInstance();
			mojo.setLog(log);

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

			return mojo;
		} catch (ReflectiveOperationException e) {
			throw new ParameterResolutionException("Failed to setup mojo " + mojoId + ".", e);
		}
	}

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
