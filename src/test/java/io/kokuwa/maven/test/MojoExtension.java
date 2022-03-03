package io.kokuwa.maven.test;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.File;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptorBuilder;
import org.codehaus.plexus.util.ReflectionUtils;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContextException;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MojoExtension implements ParameterResolver, BeforeAllCallback {

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
			log.debug("Found plugin: {}", plugin.getId());
			for (var mojo : plugin.getMojos()) {
				log.debug("Found mojo: {}", mojo.getId());
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

		var classValues = Stream
				.of(context.getRequiredTestClass().getAnnotationsByType(MojoParameter.class))
				.collect(Collectors.toMap(MojoParameter::name, MojoParameter::value));
		var methodValues = Stream
				.of(context.getRequiredTestMethod().getAnnotationsByType(MojoParameter.class))
				.collect(Collectors.toMap(MojoParameter::name, MojoParameter::value));

		try {

			log.trace("{} - create mojo", mojoId);
			var mojo = mojoType.getDeclaredConstructor().newInstance();

			for (var parameter : mojoDescriptor.getParameters()) {

				var name = parameter.getName();
				var field = ReflectionUtils.getFieldByNameIncludingSuperclasses(name, mojoType);
				field.setAccessible(true);

				var defaultValue = parameter.getDefaultValue();
				var classValue = classValues.get(name);
				var methodValue = methodValues.get(name);

				if (methodValue != null) {
					log.trace("{}#{} - set value from method: {}", mojoId, name, methodValue);
					setMojoParameterValue(mojo, field, methodValue);
				} else if (classValue != null) {
					log.trace("{}#{} - set value from class: {}", mojoId, name, classValue);
					setMojoParameterValue(mojo, field, classValue);
				} else if (defaultValue != null) {
					log.trace("{}#{} - set default value: {}", mojoId, name, defaultValue);
					setMojoParameterValue(mojo, field, defaultValue);
				} else if (parameter.isRequired()) {
					throw new ParameterResolutionException(
							"Failed to setup mojo " + mojoId + ". Parameter " + name + " is required.");
				} else {
					log.trace("{}#{} - no value set", mojoId, name);
				}
			}

			return mojo;
		} catch (ReflectiveOperationException e) {
			throw new ParameterResolutionException("Failed to setup mojo " + mojoId + ".", e);
		}
	}

	private void setMojoParameterValue(Mojo mojo, Field field, String value) throws ReflectiveOperationException {
		if (String.class.equals(field.getType())) {
			field.set(mojo, value);
		} else if (Enum.class.isAssignableFrom(field.getType())) {
			field.set(mojo, Enum.valueOf((Class<Enum>) field.getType(), value));
		} else if (File.class.equals(field.getType())) {
			field.set(mojo, new File(value));
		} else if (boolean.class.equals(field.getType())) {
			field.set(mojo, Boolean.valueOf(value));
		} else if (int.class.equals(field.getType())) {
			field.set(mojo, Integer.valueOf(value));
		} else {
			fail(field.getName() + " has unknown type: " + field.getType());
		}
	}
}