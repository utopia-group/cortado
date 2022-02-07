package edu.utexas.cs.utopia.cortado.mockclasses;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Atomic {
    /**
     * The name of the waituntil guard method. No guard method
     * is indicated by the empty string.
     * Otherwise, the class must exactly one method of
     * the provided name. That method must
     * - either take no parameters, or the same parameters as the {@link Atomic} method.
     * - return a boolean
     * - throw no exceptions.
     * - be private
     * - not be annotated {@link Atomic}
     *
     * @return the name of the guard method, or empty string if no guard method.
     */
    String waituntil() default "";
}
