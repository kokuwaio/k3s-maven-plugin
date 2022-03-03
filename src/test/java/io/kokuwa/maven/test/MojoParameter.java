package io.kokuwa.maven.test;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Repeatable(MojoParameter.MojoParameters.class)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MojoParameter {

	String name();

	String value();

	@Target({ ElementType.METHOD, ElementType.TYPE })
	@Retention(RUNTIME)
	@Documented
	@interface MojoParameters {
		MojoParameter[] value();
	}
}