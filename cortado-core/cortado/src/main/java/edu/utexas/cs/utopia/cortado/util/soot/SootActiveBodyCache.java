package edu.utexas.cs.utopia.cortado.util.soot;

import soot.Body;
import soot.SootMethod;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Interface to store {@link soot} objects which are computed
 * from active bodies of a {@link SootMethod}
 * to avoid recomputing them.
 *
 * @param <SootObjectType> the type of the {@link soot} object
 */
public abstract class SootActiveBodyCache<SootObjectType> {

    /**
     * A snapshot of a body, along with an object
     * computed from that body
     */
    protected final class BodyAndComputedObject
    {
        private final String activeBodyString;
        private final SootObjectType object;

        /**
         * @param method the method with an active body to capture a string representation of
         */
        BodyAndComputedObject(@Nonnull SootMethod method)
        {
            assert method.hasActiveBody();
            this.activeBodyString = method.getActiveBody().toString();
            this.object = SootActiveBodyCache.this.computeFromActiveBody(method);
        }

        /**
         * @param m the method to check the active body
         * @return true iff m's active body has the same string representation
         *          as this object's body
         */
        boolean bodyMatches(@Nonnull SootMethod m)
        {
            assert m.hasActiveBody();
            return Objects.equals(m.getActiveBody().toString(), activeBodyString);
        }

        /**
         * @param b the body
         * @return true iff b matches the recorded active body
         */
        protected boolean bodyMatches(@Nonnull Body b)
        {
            return b.toString().equals(activeBodyString);
        }

        /**
         * @return the object
         */
        @Nonnull
        SootObjectType getObject()
        {
            return object;
        }
    }

    private final ConcurrentMap<SootMethod, BodyAndComputedObject> cache = new ConcurrentHashMap<>();

    /**
     * Compute the object from method's active body
     *
     * @param method the method (may assume it has an active body)
     * @return the computed object
     */
    @Nonnull
    abstract protected SootObjectType computeFromActiveBody(@Nonnull SootMethod method);

    /**
     * Get the computed {@link soot} object for the method, computing one
     * and caching it if method does not have a valid cache entry.
     *
     * @param method the method (must have an active body)
     * @throws IllegalArgumentException if method has no active body (see {@link SootMethod#hasActiveBody()})
     * @return an object computed from method's current active body
     */
    @Nonnull
    public SootObjectType getOrCompute(@Nonnull SootMethod method)
    {
        // method must have an active body
        if(!method.hasActiveBody())
        {
            throw new IllegalArgumentException("method " + method.getName() + " has no active body.");
        }
        return cache.compute(method, (m, oldBodyAndObject) -> {
            if(oldBodyAndObject == null || !oldBodyAndObject.bodyMatches(m) || invalidate(oldBodyAndObject))
            {
                return new BodyAndComputedObject(m);
            }
            return oldBodyAndObject;
        }).getObject();
    }

    /**
     * Override to provide an additional reason that the cached entry may be invalid.
     *
     * Regardless of the returned value, the cache entry will be invalidated
     * if the active body has changed.
     *
     * @param bodyAndComputedObject the cache entry
     * @return If this returns true, will invalidate a cache entry.
     */
    protected boolean invalidate(@Nonnull BodyAndComputedObject bodyAndComputedObject)
    {
        return false;
    }

    /**
     * Clear the cache
     */
    public void clear()
    {
        cache.clear();
    }
}
