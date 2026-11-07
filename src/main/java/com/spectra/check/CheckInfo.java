package com.spectra.check;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Static metadata for a check. Tunable values live in checks.yml. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CheckInfo {

    /** Config key, lowercase with dashes, e.g. {@code fly-a}. */
    String id();

    /** Display name, e.g. {@code Fly (A)}. */
    String name();

    CheckCategory category();

    String description() default "";

    /** Default for the experimental flag when the check is first written to checks.yml. */
    boolean experimental() default false;
}
