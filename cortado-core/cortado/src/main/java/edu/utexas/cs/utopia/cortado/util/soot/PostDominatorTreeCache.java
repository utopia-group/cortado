package edu.utexas.cs.utopia.cortado.util.soot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootMethod;
import soot.Unit;
import soot.toolkits.graph.DominatorTree;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.MHGPostDominatorsFinder;

import javax.annotation.Nonnull;

/**
 * Cache for post-{@link DominatorTree}s
 */
public class PostDominatorTreeCache extends SootActiveBodyCache<DominatorTree<Unit>> {
    private static final Logger log = LoggerFactory.getLogger(PostDominatorTreeCache.class.getName());
    private static PostDominatorTreeCache INSTANCE = new PostDominatorTreeCache();

    /**
     * Following java singleton pattern, at most one instance of this
     * cache exists
     */
    private PostDominatorTreeCache(){}

    /**
     * Get the instance of this cache
     */
    public static PostDominatorTreeCache getInstance()
    {
        return INSTANCE;
    }

    @Nonnull
    @Override
    protected DominatorTree<Unit> computeFromActiveBody(@Nonnull SootMethod method) {
        assert method.hasActiveBody();
        final ExceptionalUnitGraph cfg = ExceptionalUnitGraphCache.getInstance().getOrCompute(method);
        // Get the post-dominator tree of the body
        MHGPostDominatorsFinder<Unit> pdomFinder = new MHGPostDominatorsFinder<>(cfg);
        return new DominatorTree<>(pdomFinder);
    }
}
