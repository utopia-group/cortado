package edu.utexas.cs.utopia.cortado.util.graph;

import com.google.common.graph.ImmutableGraph;
import edu.utexas.cs.utopia.cortado.util.soot.ExceptionalUnitGraphCache;
import edu.utexas.cs.utopia.cortado.util.soot.SootActiveBodyCache;
import soot.SootMethod;
import soot.Unit;
import soot.toolkits.graph.ExceptionalUnitGraph;

import javax.annotation.Nonnull;

@SuppressWarnings("UnstableApiUsage")
public class GuavaExceptionalUnitGraphCache extends SootActiveBodyCache<ImmutableGraph<Unit>>
{
    private static final GuavaExceptionalUnitGraphCache instance = new GuavaExceptionalUnitGraphCache();

    /**
     * singleton pattern
     */
    private GuavaExceptionalUnitGraphCache(){}

    /**
     * @return the singleton instance
     */
    public static GuavaExceptionalUnitGraphCache getInstance()
    {
        return instance;
    }

    @Nonnull
    @Override
    protected ImmutableGraph<Unit> computeFromActiveBody(@Nonnull SootMethod method)
    {
        final ExceptionalUnitGraph sootCFG = ExceptionalUnitGraphCache.getInstance().getOrCompute(method);
        return SootGraphConverter.convertToGuavaGraph(sootCFG);
    }
}
