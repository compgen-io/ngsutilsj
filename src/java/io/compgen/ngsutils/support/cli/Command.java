package io.compgen.ngsutils.support.cli;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface Command {
	String name() default "";
	String desc() default "";
	String cat() default "";
    String doc() default "";
    boolean experimental() default false;
    boolean deprecated() default false;
}
