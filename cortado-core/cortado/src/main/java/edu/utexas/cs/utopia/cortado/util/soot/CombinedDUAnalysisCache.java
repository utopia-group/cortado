package edu.utexas.cs.utopia.cortado.util.soot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootMethod;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.CombinedDUAnalysis;

import javax.annotation.Nonnull;

/**
 * {@link CombinedDUAnalysis} analyses, cached
 * by method's active bodies.
 *
 * This cache is designed as according to the singleton pattern,
 * i.e. there should exist at most one of these objects
 * in a given java runtime (see https://www.geeksforgeeks.org/singleton-class-java/ for a tutorial)
 */
public class CombinedDUAnalysisCache extends SootActiveBodyCache<CombinedDUAnalysis>
{
    private static final CombinedDUAnalysisCache INSTANCE = new CombinedDUAnalysisCache();
    private static final Logger log = LoggerFactory.getLogger(CombinedDUAnalysisCache.class.getName());

    /**
     * Make constructor private
     */
    private CombinedDUAnalysisCache() { }

    /**
     * @return the singleton instance of this class
     */
    public static CombinedDUAnalysisCache getInstance()
    {
        return INSTANCE;
    }

    @Nonnull
    @Override
    protected CombinedDUAnalysis computeFromActiveBody(@Nonnull SootMethod method) {
        assert method.hasActiveBody();
        log.debug("Computing SimpleLiveLocals for " + method.getName());
        return new CombinedDUAnalysis(new ExceptionalUnitGraph(method.getActiveBody()));
    }
}
