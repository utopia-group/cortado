package edu.utexas.cs.utopia.cortado.util.soot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootMethod;
import soot.toolkits.graph.ExceptionalUnitGraph;

import javax.annotation.Nonnull;

/**
 * Cache of {@link ExceptionalUnitGraph} (cached on active bodies).
 * Follows java singleton pattern
 */
public class ExceptionalUnitGraphCache extends SootActiveBodyCache<ExceptionalUnitGraph> {
    private static final ExceptionalUnitGraphCache INSTANCE = new ExceptionalUnitGraphCache();
    private static final Logger log = LoggerFactory.getLogger(ExceptionalUnitGraphCache.class.getName());

    /**
     * Following java singleton pattern, only one instance
     * of this cache exists at a time
     */
    private ExceptionalUnitGraphCache() {}

    /**
     * @return the singleton {@link ExceptionalUnitGraphCache}
     */
    public static ExceptionalUnitGraphCache getInstance()
    {
        return INSTANCE;
    }

    @Nonnull
    @Override
    protected ExceptionalUnitGraph computeFromActiveBody(@Nonnull SootMethod method) {
        assert method.hasActiveBody();
        final ExceptionalUnitGraph cfg = new ExceptionalUnitGraph(method.getActiveBody());
        assert cfg.getBody().toString().equals(method.getActiveBody().toString());
        return cfg;
    }

    // invalidate if cfg was modified in-place.
    @Override
    protected boolean invalidate(@Nonnull BodyAndComputedObject bodyAndComputedObject)
    {
        ExceptionalUnitGraph cfg = bodyAndComputedObject.getObject();
        return !bodyAndComputedObject.bodyMatches(cfg.getBody());
    }
}
