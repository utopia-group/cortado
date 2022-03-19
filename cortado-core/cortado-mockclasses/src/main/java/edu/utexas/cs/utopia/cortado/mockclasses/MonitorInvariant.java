package edu.utexas.cs.utopia.cortado.mockclasses;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to mark methods which are monitor invariants.
 * This method must
 * - take no parameters
 * - return a boolean
 * - throw no exceptions
 * - be private
 * - not be annotated {@link Atomic}
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface MonitorInvariant
{
}
