package edu.utexas.cs.utopia.cortado.vcgen;

import edu.utexas.cs.utopia.cortado.util.soot.SootActiveBodyCache;
import soot.SootMethod;

import javax.annotation.Nonnull;

public class CachedNormalizedBodyFactory extends SootActiveBodyCache<NormalizedMethodClone>
{
    private static final CachedNormalizedBodyFactory INSTANCE = new CachedNormalizedBodyFactory();

    private CachedNormalizedBodyFactory()
    {

    }

    public static CachedNormalizedBodyFactory getInstance()
    {
        return INSTANCE;
    }

    @Nonnull
    @Override
    protected NormalizedMethodClone computeFromActiveBody(@Nonnull SootMethod method)
    {
        return new NormalizedMethodClone(method);
    }
}
