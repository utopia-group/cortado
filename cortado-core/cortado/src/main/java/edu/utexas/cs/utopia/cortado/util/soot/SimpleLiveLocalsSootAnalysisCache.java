package edu.utexas.cs.utopia.cortado.util.soot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootMethod;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.SimpleLiveLocals;

import javax.annotation.Nonnull;

/**
 * {@link SimpleLiveLocals} analyses, cached
 * by method's active bodies.
 *
 * This cache is designed as according to the singleton pattern,
 * i.e. there should exist at most one of these objects
 * in a given java runtime (see https://www.geeksforgeeks.org/singleton-class-java/ for a tutorial)
 */
public class SimpleLiveLocalsSootAnalysisCache extends SootActiveBodyCache<SimpleLiveLocals>
{
    private static final SimpleLiveLocalsSootAnalysisCache INSTANCE = new SimpleLiveLocalsSootAnalysisCache();
    private static final Logger log = LoggerFactory.getLogger(SimpleLiveLocalsSootAnalysisCache.class.getName());

    /**
     * Make constructor private
     */
    private SimpleLiveLocalsSootAnalysisCache() { }

    /**
     * @return the singleton instance of this class
     */
    public static SimpleLiveLocalsSootAnalysisCache getInstance()
    {
        return INSTANCE;
    }

    @Nonnull
    @Override
    protected SimpleLiveLocals computeFromActiveBody(@Nonnull SootMethod method) {
        assert method.hasActiveBody();
        log.debug("Computing SimpleLiveLocals for " + method.getName());
        return new SimpleLiveLocals(new ExceptionalUnitGraph(method.getActiveBody()));
    }
}
